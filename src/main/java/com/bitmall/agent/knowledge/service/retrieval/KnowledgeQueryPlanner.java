package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.KnowledgeIntentService;
import com.bitmall.agent.knowledge.vo.KnowledgeIntentVO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把进入知识工具的问题拆成若干检索计划，每个计划只对应一个品牌 + 一个政策类别。
 *
 * <p>整个过程是确定性的规则处理，不额外调用模型：
 * <ul>
 *   <li>把"它""这款"等指代按对话历史补全成具体品牌；
 *   <li>按标点和连接词拆分子句，识别每个子句的品牌和类别；
 *   <li>核对 Agent 传来的子问题没有偏离用户原文（没有凭空加品牌、丢掉否定词或型号等）；
 *   <li>检查用户原文里的每个知识需求是否都被某个计划覆盖，遗漏的补一个"需要补问"的计划。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class KnowledgeQueryPlanner {
  private static final String BRAND_PLATFORM = "PLATFORM";
  private static final String SCOPE_UNRESOLVED = "UNRESOLVED";

  /** 子问题计划的编号前缀（q1、q2…）和原文遗漏补问计划的编号前缀（gap3…）。 */
  private static final String PLAN_ID_PREFIX = "q";

  private static final String GAP_PLAN_ID_PREFIX = "gap";

  /** 单次请求最多生成的计划数，超过说明问题太宽，要求用户缩小范围。 */
  private static final int MAX_PLANS = 30;

  /** 指代无法消解时会把这段话追加到问题末尾，后续据此识别出该子句需要补问。 */
  private static final String AMBIGUOUS_REFERENCE_MARKER = "品牌与购买渠道尚不明确";

  /** 指代词：问题里出现这些词且没有写明品牌时，尝试从对话历史中找品牌。 */
  private static final String REFERENCE_PATTERN = ".*(它|这款|这个商品|那款).*";

  /** 拆分子句用的标点和连接词。 */
  private static final String CLAUSE_SEPARATOR_PATTERN = "[，,；;。、]|以及|并且|同时|还有|和|与|及";

  /** 渠道、否定和商品状态关键词：原文子句里有的，子问题里也必须保留。 */
  private static final List<String> CONDITION_MARKERS =
      List.of("不能", "不支持", "未激活", "已激活", "未拆封", "已拆封", "官网", "官方");

  /** 识别品牌、购买渠道和政策类别。 */
  private final KnowledgeIntentService intents;

  /**
   * 生成检索计划。
   *
   * <p>每个计划只有一个明确的品牌和类别。多个品牌搭配多个类别、又说不清谁对应谁时，生成补问计划，
   * 而不是把品牌 × 类别做笛卡尔积全部查一遍（那样会把 A 品牌的问题套到 B 品牌的政策上）。
   *
   * @param trustedOriginal context 中的原问题是否来自后端可信来源（Agent 调用为 true，HTTP 直接调用为 false）
   * @throws IllegalArgumentException 计划数超过上限时
   */
  public List<Plan> plan(
      KnowledgeRetrievalRequest request,
      KnowledgeExecutionContext context,
      boolean trustedOriginal) {
    String original = context.getOriginalQuestion();
    List<String> originalReferences = new ArrayList<>();
    String constrainedOriginal =
        resolveReference(original, context.getHistory(), originalReferences);

    // 第 1 步：为请求里的每个子问题生成计划。
    List<Plan> plans = new ArrayList<>();
    for (String input : requestedQueries(request)) {
      addPlansForQuery(
          plans, input, request, context, constrainedOriginal, originalReferences, trustedOriginal);
    }

    // HTTP 旧接口带的筛选条件是调用方明确选定的范围，直接按它查，不再对照原文补齐。
    // Agent 调用时筛选条件是模型填的，仍需核对后端掌握的完整原文。
    if (isExplicitHttpFilterRequest(request, trustedOriginal)) {
      return List.copyOf(plans);
    }

    // 第 2 步：原文中明显的知识需求必须每个都有对应计划；订单状态等实时业务不算知识需求。
    Map<String, KnowledgeIntentVO> requirements =
        collectOriginalRequirements(plans, constrainedOriginal);
    addPlansForUncoveredRequirements(plans, requirements, original);

    if (plans.size() > MAX_PLANS) {
      throw new IllegalArgumentException("知识分组过多，请缩小问题范围");
    }
    return List.copyOf(plans);
  }

  /** 请求里的子问题列表；旧调用方只传单个 query。 */
  private static List<String> requestedQueries(KnowledgeRetrievalRequest request) {
    if (request.getQueries() == null) {
      return List.of(request.getQuery());
    }
    return request.getQueries();
  }

  /** 非可信调用、只传单个 query、并且带了任一筛选条件，视为 HTTP 调用方显式指定的范围。 */
  private static boolean isExplicitHttpFilterRequest(
      KnowledgeRetrievalRequest request, boolean trustedOriginal) {
    boolean hasFilter =
        request.getBrand() != null || request.getCategory() != null || request.getScope() != null;
    return !trustedOriginal && request.getQueries() == null && hasFilter;
  }

  /**
   * 为一个子问题生成计划：先补全指代，再按子句拆分，每个子句识别意图并核对是否偏离原文。
   *
   * <p>需要补问的子句生成一个补问计划；可检索的子句按"品牌 × 类别"展开（此时至多一边有多个值）。
   */
  private void addPlansForQuery(
      List<Plan> plans,
      String input,
      KnowledgeRetrievalRequest request,
      KnowledgeExecutionContext context,
      String constrainedOriginal,
      List<String> originalReferences,
      boolean trustedOriginal) {
    List<String> references = new ArrayList<>(originalReferences);
    String resolved = resolveReference(input, context.getHistory(), references);
    boolean explicitHttpBrandFilter = !trustedOriginal && request.getBrand() != null;
    for (String clause : clauses(resolved)) {
      KnowledgeIntentVO intent =
          intents.resolve(clause, request.getBrand(), request.getCategory(), request.getScope());
      String issue =
          constraintIssue(constrainedOriginal, clause, intent, explicitHttpBrandFilter, references);
      if (clause.contains(AMBIGUOUS_REFERENCE_MARKER)) {
        issue = "指代主体有歧义，请补充具体品牌或商品。";
      }
      if (issue != null) {
        intent = unresolved(intent, issue);
      }
      boolean ambiguousPairing =
          intent.getBrands().size() > 1 && intent.getCategories().size() > 1;
      if (ambiguousPairing) {
        intent = unresolved(intent, "多个品牌与多个政策类别的对应关系不明确，请分别说明。");
      }

      if (intent.isClarificationRequired()) {
        plans.add(new Plan(nextId(PLAN_ID_PREFIX, plans), clause, intent, references));
      } else {
        for (String brand : intent.getBrands()) {
          for (String type : intent.getCategories()) {
            KnowledgeIntentVO single =
                new KnowledgeIntentVO(
                    List.of(brand), List.of(type), intent.getScope(), false, intent.getMessage());
            plans.add(new Plan(nextId(PLAN_ID_PREFIX, plans), clause, single, references));
          }
        }
      }
    }
  }

  /**
   * 从原问题的子句中提取"品牌/类别"知识需求，供后续检查是否被计划覆盖。
   *
   * <p>注意：遇到未收录品牌的子句时，如果当前还没有任何补问计划，会直接向 plans 追加一个补问计划；
   * 后续子句的判断会看到这个新计划，所以这一步必须按子句顺序逐个处理。
   *
   * @return "品牌/类别" → 需求意图，保持原文出现顺序
   */
  private Map<String, KnowledgeIntentVO> collectOriginalRequirements(
      List<Plan> plans, String constrainedOriginal) {
    Map<String, KnowledgeIntentVO> requirements = new LinkedHashMap<>();
    for (String clause : clauses(constrainedOriginal)) {
      boolean isKnowledgeQuestion = !intents.categories(clause).isEmpty();
      if (!isKnowledgeQuestion) {
        continue;
      }
      KnowledgeIntentVO intent = intents.resolve(clause, null, null, null);
      if (intent.isClarificationRequired()) {
        boolean noClarificationPlanYet =
            plans.stream().noneMatch(plan -> plan.getIntent().isClarificationRequired());
        if (intents.unknownBrand(clause) && noClarificationPlanYet) {
          plans.add(new Plan(nextId(GAP_PLAN_ID_PREFIX, plans), clause, intent, List.of()));
        }
        continue;
      }
      // 多品牌 × 多类别说不清对应关系，子问题阶段已经要求补问，这里不再追加需求。
      boolean ambiguousPairing =
          intent.getBrands().size() > 1 && intent.getCategories().size() > 1;
      if (ambiguousPairing) {
        continue;
      }
      // 子句既没写品牌也没说本站（主语被省略），而已有计划带了品牌时，视为承接前文的品牌，
      // 不把它当成一条独立的本站规则需求。
      boolean clauseHasNoSubject = intents.brands(clause).isEmpty() && !intents.platform(clause);
      boolean someBrandedPlanExists =
          plans.stream().anyMatch(plan -> !intents.brands(plan.getQuery()).isEmpty());
      if (clauseHasNoSubject && someBrandedPlanExists) {
        continue;
      }
      for (String brand : intent.getBrands()) {
        for (String type : intent.getCategories()) {
          requirements.put(
              brand + "/" + type,
              new KnowledgeIntentVO(
                  List.of(brand), List.of(type), intent.getScope(), false, "原问题中的知识需求没有被子问题覆盖"));
        }
      }
    }
    return requirements;
  }

  /** 原文需求没有被任何可检索计划覆盖时，追加一个以原问题为查询的补问计划。 */
  private void addPlansForUncoveredRequirements(
      List<Plan> plans, Map<String, KnowledgeIntentVO> requirements, String original) {
    for (Map.Entry<String, KnowledgeIntentVO> entry : requirements.entrySet()) {
      String requirementKey = entry.getKey();
      boolean covered =
          plans.stream()
              .anyMatch(
                  plan ->
                      !plan.getIntent().isClarificationRequired()
                          && key(plan.getIntent()).equals(requirementKey));
      if (covered) {
        continue;
      }
      KnowledgeIntentVO requirement = entry.getValue();
      KnowledgeIntentVO missing =
          new KnowledgeIntentVO(
              requirement.getBrands(),
              requirement.getCategories(),
              requirement.getScope(),
              true,
              "原问题中的品牌/类别被遗漏，请分别继续检索。");
      plans.add(new Plan(nextId(GAP_PLAN_ID_PREFIX, plans), original, missing, List.of()));
    }
  }

  /** 计划编号按生成顺序递增：第 n 个计划编号为前缀 + n（q 与 gap 共用同一序号）。 */
  private static String nextId(String prefix, List<Plan> plans) {
    return prefix + (plans.size() + 1);
  }

  /** 保留原意图识别出的品牌和类别，但标记为需要补问。 */
  private static KnowledgeIntentVO unresolved(KnowledgeIntentVO intent, String message) {
    return new KnowledgeIntentVO(
        intent.getBrands(), intent.getCategories(), SCOPE_UNRESOLVED, true, message);
  }

  /**
   * 检查子问题是否偏离用户原文，偏离时返回给用户的补问提示，否则返回 null。
   *
   * <p>依次检查：品牌是否凭空出现或被替换、本站购买是否被改成品牌官方渠道、地区是否超出收录范围、
   * 是否新增了数字/型号、原文中的否定或商品状态条件是否被丢掉。
   *
   * @param original 已补全指代的原问题
   * @param sub 待检查的子问题子句
   * @param explicitHttpFilter HTTP 调用方是否显式指定了品牌（此时允许原文中没有品牌）
   * @param references 子问题从对话历史中补全的品牌引用
   */
  private String constraintIssue(
      String original,
      String sub,
      KnowledgeIntentVO intent,
      boolean explicitHttpFilter,
      List<String> references) {
    List<String> originalBrands = intents.brands(original);
    List<String> selectedBrands = intents.brands(sub);
    if (selectedBrands.isEmpty() && !intent.isClarificationRequired()) {
      selectedBrands = intent.getBrands();
    }
    boolean selectsOnlyPlatform = selectedBrands.equals(List.of(BRAND_PLATFORM));
    boolean originalIsPlatform = intents.platform(original);

    // 原文和对话上下文都没提品牌，子问题却指定了某个品牌。
    boolean brandFromNowhere =
        originalBrands.isEmpty()
            && !selectedBrands.isEmpty()
            && !selectsOnlyPlatform
            && references.isEmpty()
            && !explicitHttpFilter;
    if (brandFromNowhere) {
      return "原问题和可追溯上下文没有这个品牌，请先确认品牌，不能猜测。";
    }

    // 原文提了品牌，子问题却出现原文没有的品牌（原文是本站购买、子问题为 PLATFORM 的情况除外）。
    boolean platformMatchesOriginal = selectsOnlyPlatform && originalIsPlatform;
    boolean addsUnmentionedBrand =
        !originalBrands.isEmpty()
            && !originalBrands.containsAll(selectedBrands)
            && !platformMatchesOriginal;
    if (addsUnmentionedBrand) {
      return "子问题增加了原问题未提到的品牌，请保留原始诉求。";
    }

    if (originalIsPlatform && !intents.platform(sub)) {
      return "原问题涉及本站购买，子问题不能切换为品牌官方渠道。";
    }
    if (intents.outsideSupportedRegion(original)) {
      return "原问题地区尚未收录，请确认购买地区，不能删去地区后套用中国大陆规则。";
    }
    if (!KnowledgeText.identifiers(original).containsAll(KnowledgeText.identifiers(sub))) {
      return "子问题新增了原问题没有的数字或型号，请保留原始条件。";
    }

    // 只对和本子问题相关的原文子句检查条件词：跳过属于其他品牌的子句和同时提到多个品牌的子句。
    for (String source : clauses(original)) {
      List<String> sourceBrands = intents.brands(source);
      boolean aboutOtherBrand =
          !sourceBrands.isEmpty()
              && !selectedBrands.isEmpty()
              && Collections.disjoint(sourceBrands, selectedBrands);
      if (aboutOtherBrand) {
        continue;
      }
      if (sourceBrands.size() > 1) {
        continue;
      }
      for (String marker : CONDITION_MARKERS) {
        if (source.contains(marker) && !sub.contains(marker)) {
          return "子问题遗漏了原问题中的渠道、否定或商品状态，请保留条件。";
        }
      }
    }
    return null;
  }

  /**
   * 把问题拆成子句；只有每个政策子句都能明确对应到某个主体时才拆，否则整句保留。
   *
   * <p>例如"苹果与耐克退货"拆开后"苹果"这一段没有政策类别（品牌省略了后半句），
   * 此时整句保留，交给后续按同一类别分别查两个品牌。拆分成功时只返回带政策类别的子句，
   * 实时订单、商品查询等片段会被丢掉，因为它们不是知识需求。
   */
  public List<String> clauses(String text) {
    List<String> pieces = List.of(text.split(CLAUSE_SEPARATOR_PATTERN));
    List<String> meaningful =
        pieces.stream().map(String::trim).filter(piece -> !piece.isEmpty()).toList();
    if (meaningful.size() > 1) {
      boolean hasBareBrand =
          meaningful.stream()
              .anyMatch(
                  piece -> !intents.brands(piece).isEmpty() && intents.categories(piece).isEmpty());
      List<String> policyClauses =
          meaningful.stream().filter(piece -> !intents.categories(piece).isEmpty()).toList();
      boolean everyPolicyClauseHasSubject =
          policyClauses.stream()
              .allMatch(
                  piece ->
                      !intents.brands(piece).isEmpty()
                          || intents.platform(piece)
                          || intents.unknownBrand(piece));
      if (!hasBareBrand && !policyClauses.isEmpty() && everyPolicyClauseHasSubject) {
        return policyClauses;
      }
    }
    return List.of(text);
  }

  /**
   * 补全问题里的指代（"它""这款"等）。
   *
   * <p>问题已写明品牌或本站、或没有指代词时原样返回。否则在用户历史消息中找品牌：恰好找到一个就把品牌
   * 加到问题前面，并把来源记入 references；找不到或找到多个时，在问题末尾追加歧义标记，后续会要求补问。
   */
  private String resolveReference(String query, List<String> history, List<String> references) {
    boolean needsResolution =
        intents.brands(query).isEmpty()
            && !intents.platform(query)
            && query.matches(REFERENCE_PATTERN);
    if (!needsResolution) {
      return query;
    }
    Map<String, String> brandToMessage = new LinkedHashMap<>();
    for (String message : history) {
      if (!message.startsWith("user:")) {
        continue;
      }
      for (String brand : intents.brands(message)) {
        brandToMessage.put(brand, message);
      }
    }
    if (brandToMessage.size() == 1) {
      Map.Entry<String, String> only = brandToMessage.entrySet().iterator().next();
      references.add(only.getKey() + " <- " + only.getValue());
      return only.getKey() + " " + query;
    }
    return query + "（" + AMBIGUOUS_REFERENCE_MARKER + "，请补充品牌）";
  }

  /** 意图的"品牌/类别"键，多个值用逗号连接，例如 "APPLE/RETURNS"。 */
  public static String key(KnowledgeIntentVO intent) {
    return String.join(",", intent.getBrands()) + "/" + String.join(",", intent.getCategories());
  }

  /** 一个检索计划（不可变），只在知识模块内部使用。 */
  @Getter
  public static final class Plan {
    /** 计划编号，同时作为结果分组的 queryId。 */
    private final String id;

    /** 实际检索用的问题文本。 */
    private final String query;

    /** 已归一化的品牌、类别和范围；可检索计划中品牌和类别都只有一个。 */
    private final KnowledgeIntentVO intent;

    /** 从对话历史补全指代时使用的来源，格式为 "品牌 <- 原消息"。 */
    private final List<String> references;

    public Plan(String id, String query, KnowledgeIntentVO intent, List<String> references) {
      this.id = id;
      this.query = query;
      this.intent = intent;
      this.references = List.copyOf(references);
    }
  }
}
