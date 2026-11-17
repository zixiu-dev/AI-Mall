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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** 外层 Agent 的只读政策工具：原意约束→有限增强→双路召回→RRF→证据打包，不建立第二个 Agent 循环。 */
@Service
public class KnowledgeService {
  private final KnowledgeIntentService intents;
  private final KnowledgeQueryPlanner planner;
  private final KnowledgeRetriever retriever;
  private final QueryEnhancer enhancer;
  private final KnowledgeProperties properties;

  public KnowledgeService(
      KnowledgeIntentService intents,
      KnowledgeQueryPlanner planner,
      KnowledgeRetriever retriever,
      QueryEnhancer enhancer,
      KnowledgeProperties properties) {
    this.intents = intents;
    this.planner = planner;
    this.retriever = retriever;
    this.enhancer = enhancer;
    this.properties = properties;
  }

  /** 兼容历史单 query 调用，HTTP 原文由本次请求本身提供。 */
  public List<KnowledgeHitVO> search(String query) {
    return retrieve(query, null, null, null).getHits();
  }

  public KnowledgeSearchResultVO retrieve(
      String query, String brand, String category, String scope) {
    return retrieve(new KnowledgeRetrievalRequest(query, null, brand, category, scope), null);
  }

  public KnowledgeSearchResultVO retrieve(
      KnowledgeRetrievalRequest request, KnowledgeExecutionContext trusted) {
    if (!List.of("KEYWORD", "HYBRID").contains(properties.getMode()))
      throw new IllegalStateException("知识检索模式只支持 KEYWORD 或 HYBRID");
    var context =
        trusted == null
            ? new KnowledgeExecutionContext(
                UUID.randomUUID().toString(),
                request.getQuery(),
                List.of(),
                properties.getTimeoutMillis())
            : trusted;
    context.enter(properties.getMaximumCalls());
    long deadline =
        Math.min(
            context.getDeadlineNanos(),
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getTimeoutMillis()));
    // 先确定合法品牌/类别组；增强只能换表达，不能修改这里的业务范围。
    var plans = planner.plan(request, context, trusted != null);
    var warnings = new ArrayList<String>();
    var variants = new LinkedHashMap<String, List<String>>();
    var uncached = new LinkedHashMap<String, String>();
    var keys = new LinkedHashMap<String, String>();
    for (var plan : plans) {
      String key =
          "enhance:"
              + KnowledgeText.hash(
                  plan.getQuery().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                      + "|"
                      + KnowledgeQueryPlanner.key(plan.getIntent())
                      + "|"
                      + plan.getIntent().getScope()
                      + "|"
                      + String.join("|", context.getHistory()));
      keys.put(plan.getId(), key);
      Object cached = context.getCache().get(key);
      if (cached instanceof List<?> list) {
        variants.put(plan.getId(), list.stream().map(String.class::cast).toList());
        continue;
      }
      variants.put(plan.getId(), List.of(plan.getQuery()));
      if (!plan.getIntent().isClarificationRequired()) uncached.put(plan.getId(), plan.getQuery());
    }
    // 未缓存子问题合并为一次模型调用，所有排队与网络等待都占用同一剩余预算。
    if (properties.isEnhancementEnabled() && !uncached.isEmpty()) {
      try {
        long enhancementDeadline =
            Math.min(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500));
        var enhanced =
            retriever.bounded(
                () -> enhancer.enhance(uncached, context.getHistory(), enhancementDeadline),
                enhancementDeadline);
        if (!enhanced.keySet().equals(uncached.keySet()))
          throw new IllegalStateException("增强返回 queryId 不符");
        for (var plan : plans) {
          if (!uncached.containsKey(plan.getId())) continue;
          var output = enhanced.get(plan.getId());
          var accepted = new java.util.LinkedHashSet<String>();
          accepted.add(plan.getQuery());
          if (output == null || output.size() > 2) throw new IllegalStateException("增强表达式数量超限");
          boolean valid = output.stream().allMatch(q -> safeVariant(plan.getQuery(), q));
          if (valid) accepted.addAll(output);
          else warnings.add("ENHANCEMENT_REJECTED:" + plan.getId() + "，新增/删除原意约束，已使用原表达");
          variants.put(plan.getId(), List.copyOf(accepted));
          context.getCache().put(keys.get(plan.getId()), List.copyOf(accepted));
        }
      } catch (RuntimeException e) {
        warnings.add("ENHANCEMENT_FALLBACK：批量改写超时、格式错误或不可用，使用原表达");
      }
    }
    // 缓存按当前源文档和已发布索引失效；本轮重复查询也不能读取已停用文档。
    String version =
        plans.stream().allMatch(p -> p.getIntent().isClarificationRequired())
            ? "CLARIFICATION"
            : retriever.bounded(retriever::cacheVersion, deadline);
    var pending = new ArrayList<KnowledgeQueryPlanner.Plan>();
    var resultKeys = new LinkedHashMap<String, String>();
    var ready = new LinkedHashMap<String, KnowledgeGroupVO>();
    for (var plan : plans) {
      String key =
          "retrieval:"
              + KnowledgeText.hash(
                  keys.get(plan.getId())
                      + "|"
                      + variants.get(plan.getId())
                      + "|"
                      + version
                      + "|"
                      + properties.getMode());
      resultKeys.put(plan.getId(), key);
      Object cached = context.getCache().get(key);
      if (cached instanceof KnowledgeGroupVO group)
        ready.put(
            plan.getId(),
            new KnowledgeGroupVO(
                plan.getId(),
                plan.getQuery(),
                group.getStatus(),
                group.getMessage(),
                group.getRetrievalMode(),
                group.getIntent(),
                group.getHits(),
                group.getWarnings(),
                group.getVariants(),
                plan.getReferences()));
      else pending.add(plan);
    }
    if (!pending.isEmpty())
      for (var group : retriever.retrieve(pending, variants, deadline)) {
        ready.put(group.getQueryId(), group);
        if (List.of("FOUND", "NO_MATCH").contains(group.getStatus())
            && group.getWarnings().isEmpty())
          context.getCache().put(resultKeys.get(group.getQueryId()), group);
      }
    var groups = plans.stream().map(p -> ready.get(p.getId())).toList();
    for (var group : groups) warnings.addAll(group.getWarnings());
    return KnowledgeEvidencePacker.limit(
        new KnowledgeSearchResultVO(groups, warnings.stream().distinct().toList()), 6);
  }

  /** 增强只能改变说法；显式品牌、政策类别、渠道、数字/型号和否定不能被改写悄悄删除。 */
  private boolean safeVariant(String original, String candidate) {
    if (candidate == null || candidate.isBlank() || candidate.length() > 500) return false;
    if (!intents.brands(original).equals(intents.brands(candidate))
        || !intents.categories(original).equals(intents.categories(candidate))
        || intents.platform(original) != intents.platform(candidate)
        || intents.unknownBrand(candidate)) return false;
    for (String marker :
        List.of(
            "不", "未", "非", "不能", "激活", "拆封", "官网", "官方", "中国", "大陆", "无条件", "一定", "保证", "任何", "均可",
            "支持", "允许", "可以"))
      if (original.contains(marker) != candidate.contains(marker)) return false;
    if (intents.outsideSupportedRegion(candidate)
        || !KnowledgeText.identifiers(original).equals(KnowledgeText.identifiers(candidate)))
      return false;
    return true;
  }
}
