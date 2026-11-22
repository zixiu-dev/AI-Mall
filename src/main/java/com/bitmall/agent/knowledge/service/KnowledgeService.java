package com.bitmall.agent.knowledge.service;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeEvidencePacker;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeQueryPlanner;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeRetriever;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeText;
import com.bitmall.agent.knowledge.service.retrieval.QueryEnhancer;
import com.bitmall.agent.knowledge.vo.KnowledgeGroupVO;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 政策知识检索服务，作为外层 Agent 的一个只读工具，也供 HTTP 接口直接调用。
 *
 * <p>一次检索的流程：
 * <ol>
 *   <li>按用户原文拆出检索计划，确定每组的品牌和政策类别（业务范围在这一步定死）；
 *   <li>可选地调用模型改写查询表达（只换说法，不能改范围），结果按请求缓存；
 *   <li>关键词 + 向量两路召回并融合，结果按请求缓存；
 *   <li>把证据裁剪到预算内返回。
 * </ol>
 * 这里不会再启动一个 Agent 循环。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {
  private static final List<String> SUPPORTED_MODES = List.of("KEYWORD", "HYBRID");

  /** 查询增强单独的时间上限，同时不超过整体截止时间。 */
  private static final long ENHANCEMENT_TIMEOUT_MILLIS = 1500;

  /** 每个计划最多接受的改写表达数（不含原表达）。 */
  private static final int MAX_ENHANCED_VARIANTS = 2;

  /** 改写表达的最大长度。 */
  private static final int MAX_VARIANT_LENGTH = 500;

  /** 返回结果的顶层 hits 最多保留的证据条数。 */
  private static final int MAX_RESULT_HITS = 6;

  /** 所有计划都需要补问时不做检索，用这个固定值代替检索缓存版本号。 */
  private static final String CACHE_VERSION_CLARIFICATION = "CLARIFICATION";

  private static final String ENHANCEMENT_CACHE_PREFIX = "enhance:";
  private static final String RETRIEVAL_CACHE_PREFIX = "retrieval:";

  /** 只有这两种状态且没有警告的分组才会被缓存，避免把降级或故障结果复用到后续调用。 */
  private static final List<String> CACHEABLE_STATUSES = List.of("FOUND", "NO_MATCH");

  /** 否定、商品状态、渠道、地区和承诺类词语：改写前后在这些词上必须一致，否则可能改变原意。 */
  private static final List<String> MEANING_MARKERS =
      List.of(
          "不", "未", "非", "不能", "激活", "拆封", "官网", "官方", "中国", "大陆", "无条件", "一定", "保证", "任何", "均可",
          "支持", "允许", "可以");

  /** 识别品牌、购买渠道和政策类别，用于校验改写表达。 */
  private final KnowledgeIntentService intents;

  /** 把问题拆成品牌 + 类别明确的检索计划。 */
  private final KnowledgeQueryPlanner planner;

  /** 执行关键词与向量召回并融合结果，同时提供有界线程池。 */
  private final KnowledgeRetriever retriever;

  /** 调用模型批量改写查询表达。 */
  private final QueryEnhancer enhancer;

  /** 读取检索模式（关键词/混合）、单次检索超时、是否启用查询改写，以及每轮对话的检索调用次数上限。 */
  private final KnowledgeProperties properties;

  /** 兼容历史的单 query 调用，只返回顶层 hits；原问题就是这次请求本身。 */
  public List<KnowledgeHitVO> search(String query) {
    return retrieve(query, null, null, null).getHits();
  }

  /** HTTP 单 query + 可选筛选条件的调用入口。 */
  public KnowledgeSearchResultVO retrieve(
      String query, String brand, String category, String scope) {
    return retrieve(new KnowledgeRetrievalRequest(query, null, brand, category, scope), null);
  }

  /**
   * 执行一次知识检索。
   *
   * @param trusted Agent 调用时传入的可信上下文（包含后端掌握的原问题、对话历史、截止时间和请求内缓存）；
   *     HTTP 直接调用传 null，此时以请求里的 query 作为原问题新建上下文
   * @throws IllegalStateException 检索模式配置非法、调用次数超限、或检索基础设施整体不可用时
   */
  public KnowledgeSearchResultVO retrieve(
      KnowledgeRetrievalRequest request, KnowledgeExecutionContext trusted) {
    if (!SUPPORTED_MODES.contains(properties.getMode())) {
      throw new IllegalStateException("知识检索模式只支持 KEYWORD 或 HYBRID");
    }
    KnowledgeExecutionContext context = trusted == null ? newContext(request) : trusted;
    context.enter(properties.getMaximumCalls());
    // 截止时间取"整个请求的截止时间"和"本次调用的超时"中更早的一个。
    long deadline =
        Math.min(
            context.getDeadlineNanos(),
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getTimeoutMillis()));

    // 第 1 步：先确定合法的品牌/类别分组；后面的增强只能换说法，不能改这里定下的业务范围。
    List<KnowledgeQueryPlanner.Plan> plans = planner.plan(request, context, trusted != null);
    List<String> warnings = new ArrayList<>();

    // 第 2 步：查询增强。先读缓存，未命中且需要检索的计划合并成一次模型调用。
    Map<String, String> enhancementKeys = new LinkedHashMap<>();
    Map<String, List<String>> variants = new LinkedHashMap<>();
    Map<String, String> uncached = new LinkedHashMap<>();
    for (KnowledgeQueryPlanner.Plan plan : plans) {
      String key = enhancementCacheKey(plan, context);
      enhancementKeys.put(plan.getId(), key);
      Object cached = context.getCache().get(key);
      if (cached instanceof List<?> list) {
        variants.put(plan.getId(), list.stream().map(String.class::cast).toList());
        continue;
      }
      variants.put(plan.getId(), List.of(plan.getQuery()));
      if (!plan.getIntent().isClarificationRequired()) {
        uncached.put(plan.getId(), plan.getQuery());
      }
    }
    if (properties.isEnhancementEnabled() && !uncached.isEmpty()) {
      try {
        enhanceUncached(plans, uncached, enhancementKeys, variants, warnings, context, deadline);
      } catch (RuntimeException e) {
        warnings.add("ENHANCEMENT_FALLBACK：批量改写超时、格式错误或不可用，使用原表达");
      }
    }

    // 第 3 步：召回。缓存版本号随启用文档和已发布索引变化，保证本轮重复查询也读不到已停用的文档。
    String cacheVersion = resultCacheVersion(plans, deadline);
    List<KnowledgeQueryPlanner.Plan> pending = new ArrayList<>();
    Map<String, String> resultKeys = new LinkedHashMap<>();
    Map<String, KnowledgeGroupVO> ready = new LinkedHashMap<>();
    for (KnowledgeQueryPlanner.Plan plan : plans) {
      String key =
          retrievalCacheKey(
              enhancementKeys.get(plan.getId()), variants.get(plan.getId()), cacheVersion);
      resultKeys.put(plan.getId(), key);
      Object cached = context.getCache().get(key);
      if (cached instanceof KnowledgeGroupVO group) {
        ready.put(plan.getId(), rebindToPlan(group, plan));
      } else {
        pending.add(plan);
      }
    }
    if (!pending.isEmpty()) {
      for (KnowledgeGroupVO group : retriever.retrieve(pending, variants, deadline)) {
        ready.put(group.getQueryId(), group);
        if (isCacheable(group)) {
          context.getCache().put(resultKeys.get(group.getQueryId()), group);
        }
      }
    }

    // 第 4 步：按计划顺序组装结果，汇总各组警告并裁剪证据。
    List<KnowledgeGroupVO> groups = plans.stream().map(plan -> ready.get(plan.getId())).toList();
    for (KnowledgeGroupVO group : groups) {
      warnings.addAll(group.getWarnings());
    }
    return KnowledgeEvidencePacker.limit(
        new KnowledgeSearchResultVO(groups, warnings.stream().distinct().toList()),
        MAX_RESULT_HITS);
  }

  /** HTTP 直接调用时没有可信上下文，以请求的 query 作为原问题、空对话历史新建一个。 */
  private KnowledgeExecutionContext newContext(KnowledgeRetrievalRequest request) {
    return new KnowledgeExecutionContext(
        UUID.randomUUID().toString(), request.getQuery(), List.of(), properties.getTimeoutMillis());
  }

  /** 增强结果缓存键：问题（去首尾空白、合并空白、转小写）+ 品牌/类别 + 范围 + 对话历史。 */
  private static String enhancementCacheKey(
      KnowledgeQueryPlanner.Plan plan, KnowledgeExecutionContext context) {
    String normalizedQuery =
        plan.getQuery().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    return ENHANCEMENT_CACHE_PREFIX
        + KnowledgeText.hash(
            String.join(
                "|",
                normalizedQuery,
                KnowledgeQueryPlanner.key(plan.getIntent()),
                plan.getIntent().getScope(),
                String.join("|", context.getHistory())));
  }

  /** 检索结果缓存键：增强缓存键 + 实际使用的查询表达 + 源数据版本 + 检索模式。 */
  private String retrievalCacheKey(
      String enhancementKey, List<String> queryVariants, String cacheVersion) {
    return RETRIEVAL_CACHE_PREFIX
        + KnowledgeText.hash(
            String.join(
                "|",
                enhancementKey,
                String.valueOf(queryVariants),
                cacheVersion,
                properties.getMode()));
  }

  /**
   * 对未命中缓存的计划批量调用模型改写，把通过校验的表达并入 variants 并写入缓存。
   *
   * <p>所有排队和网络等待都计入同一个时间预算。某个计划的改写违反原意时只对该计划回退为原表达并记警告；
   * 返回格式不对（queryId 不符、表达数超限）则抛异常，由调用方整体回退。注意抛异常前已处理的计划会保留
   * 它们的改写结果和缓存。
   */
  private void enhanceUncached(
      List<KnowledgeQueryPlanner.Plan> plans,
      Map<String, String> uncached,
      Map<String, String> enhancementKeys,
      Map<String, List<String>> variants,
      List<String> warnings,
      KnowledgeExecutionContext context,
      long deadline) {
    long enhancementDeadline =
        Math.min(
            deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ENHANCEMENT_TIMEOUT_MILLIS));
    Map<String, List<String>> enhanced =
        retriever.bounded(
            () -> enhancer.enhance(uncached, context.getHistory(), enhancementDeadline),
            enhancementDeadline);
    if (!enhanced.keySet().equals(uncached.keySet())) {
      throw new IllegalStateException("增强返回 queryId 不符");
    }
    for (KnowledgeQueryPlanner.Plan plan : plans) {
      if (!uncached.containsKey(plan.getId())) {
        continue;
      }
      List<String> rewrites = enhanced.get(plan.getId());
      // 原表达始终排第一，改写结果去重后追加在后面。
      Set<String> accepted = new LinkedHashSet<>();
      accepted.add(plan.getQuery());
      if (rewrites == null || rewrites.size() > MAX_ENHANCED_VARIANTS) {
        throw new IllegalStateException("增强表达式数量超限");
      }
      boolean allRewritesSafe =
          rewrites.stream().allMatch(rewrite -> safeVariant(plan.getQuery(), rewrite));
      if (allRewritesSafe) {
        accepted.addAll(rewrites);
      } else {
        warnings.add("ENHANCEMENT_REJECTED:" + plan.getId() + "，新增/删除原意约束，已使用原表达");
      }
      variants.put(plan.getId(), List.copyOf(accepted));
      context.getCache().put(enhancementKeys.get(plan.getId()), List.copyOf(accepted));
    }
  }

  /** 检索缓存的版本号；全部计划都需要补问时不会检索，无需读取源数据版本。 */
  private String resultCacheVersion(List<KnowledgeQueryPlanner.Plan> plans, long deadline) {
    boolean allNeedClarification =
        plans.stream().allMatch(plan -> plan.getIntent().isClarificationRequired());
    if (allNeedClarification) {
      return CACHE_VERSION_CLARIFICATION;
    }
    return retriever.bounded(retriever::cacheVersion, deadline);
  }

  /** 缓存命中的分组可能来自另一个计划，换成当前计划的 id、问题和指代来源后再使用。 */
  private static KnowledgeGroupVO rebindToPlan(
      KnowledgeGroupVO cached, KnowledgeQueryPlanner.Plan plan) {
    return new KnowledgeGroupVO(
        plan.getId(),
        plan.getQuery(),
        cached.getStatus(),
        cached.getMessage(),
        cached.getRetrievalMode(),
        cached.getIntent(),
        cached.getHits(),
        cached.getWarnings(),
        cached.getVariants(),
        plan.getReferences());
  }

  private static boolean isCacheable(KnowledgeGroupVO group) {
    return CACHEABLE_STATUSES.contains(group.getStatus()) && group.getWarnings().isEmpty();
  }

  /**
   * 判断一条改写表达是否保留了原意。
   *
   * <p>增强只允许换说法：改写前后的品牌、政策类别、是否本站购买、否定/状态等关键词、数字和型号都必须一致，
   * 也不能引入未收录品牌或境外地区。否则这条改写会悄悄扩大或缩小检索范围。
   */
  private boolean safeVariant(String original, String candidate) {
    if (candidate == null || candidate.isBlank() || candidate.length() > MAX_VARIANT_LENGTH) {
      return false;
    }
    boolean sameIntent =
        intents.brands(original).equals(intents.brands(candidate))
            && intents.categories(original).equals(intents.categories(candidate))
            && intents.platform(original) == intents.platform(candidate);
    if (!sameIntent || intents.unknownBrand(candidate)) {
      return false;
    }
    for (String marker : MEANING_MARKERS) {
      if (original.contains(marker) != candidate.contains(marker)) {
        return false;
      }
    }
    if (intents.outsideSupportedRegion(candidate)) {
      return false;
    }
    return KnowledgeText.identifiers(original).equals(KnowledgeText.identifiers(candidate));
  }
}
