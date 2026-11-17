package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.KnowledgeIntentService;
import com.bitmall.agent.knowledge.vo.KnowledgeIntentVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** 只规划已进入知识工具的问题：确定性归一化、原文约束及品牌/类别配对，不额外调用 Planner 模型。 */
@Component
public class KnowledgeQueryPlanner {
  private final KnowledgeIntentService intents;

  public KnowledgeQueryPlanner(KnowledgeIntentService intents) {
    this.intents = intents;
  }

  /** 每组只有一个明确品牌和类别；无法可靠配对时补问，不能生成品牌×类别的无条件笛卡尔积。 */
  public List<Plan> plan(
      KnowledgeRetrievalRequest request,
      KnowledgeExecutionContext context,
      boolean trustedOriginal) {
    String original = context.getOriginalQuestion();
    var originalReferences = new ArrayList<String>();
    String constrainedOriginal =
        resolveReference(original, context.getHistory(), originalReferences);
    var plans = new ArrayList<Plan>();
    int id = 0;
    for (String input :
        request.getQueries() == null ? List.of(request.getQuery()) : request.getQueries()) {
      var refs = new ArrayList<String>(originalReferences);
      String resolved = resolveReference(input, context.getHistory(), refs);
      List<String> clauses = clauses(resolved);
      for (String clause : clauses) {
        var intent =
            intents.resolve(clause, request.getBrand(), request.getCategory(), request.getScope());
        String issue =
            constraintIssue(
                constrainedOriginal,
                clause,
                intent,
                !trustedOriginal && request.getBrand() != null,
                refs);
        if (clause.contains("品牌与购买渠道尚不明确")) issue = "指代主体有歧义，请补充具体品牌或商品。";
        if (issue != null)
          intent =
              new KnowledgeIntentVO(
                  intent.getBrands(), intent.getCategories(), "UNRESOLVED", true, issue);
        if (intent.getBrands().size() > 1 && intent.getCategories().size() > 1)
          intent =
              new KnowledgeIntentVO(
                  intent.getBrands(),
                  intent.getCategories(),
                  "UNRESOLVED",
                  true,
                  "多个品牌与多个政策类别的对应关系不明确，请分别说明。");
        if (intent.isClarificationRequired()) {
          plans.add(new Plan("q" + (++id), clause, intent, refs));
          continue;
        }
        for (String brand : intent.getBrands())
          for (String type : intent.getCategories())
            plans.add(
                new Plan(
                    "q" + (++id),
                    clause,
                    new KnowledgeIntentVO(
                        List.of(brand),
                        List.of(type),
                        intent.getScope(),
                        false,
                        intent.getMessage()),
                    refs));
      }
    }
    // HTTP 旧筛选是调用方明确选择的范围；Agent 的模型筛选仍须核对后端完整原文。
    if (!trustedOriginal
        && request.getQueries() == null
        && (request.getBrand() != null
            || request.getCategory() != null
            || request.getScope() != null)) return List.copyOf(plans);
    // 原问题中的明显知识需求必须逐组有交代；订单状态等实时业务不计入 RAG 覆盖率。
    var requirements = new LinkedHashMap<String, KnowledgeIntentVO>();
    for (String clause : clauses(constrainedOriginal)) {
      if (intents.categories(clause).isEmpty()) continue;
      var intent = intents.resolve(clause, null, null, null);
      if (intent.isClarificationRequired()) {
        if (intents.unknownBrand(clause)
            && plans.stream().noneMatch(p -> p.intent.isClarificationRequired()))
          plans.add(new Plan("gap" + (++id), clause, intent, List.of()));
        continue;
      }
      if (intent.getBrands().size() > 1 && intent.getCategories().size() > 1) continue;
      if (intents.brands(clause).isEmpty()
          && !intents.platform(clause)
          && plans.stream().anyMatch(p -> !intents.brands(p.query).isEmpty())) continue;
      for (String brand : intent.getBrands())
        for (String type : intent.getCategories())
          requirements.put(
              brand + "/" + type,
              new KnowledgeIntentVO(
                  List.of(brand), List.of(type), intent.getScope(), false, "原问题中的知识需求没有被子问题覆盖"));
    }
    for (var entry : requirements.entrySet())
      if (plans.stream()
          .noneMatch(
              p -> !p.intent.isClarificationRequired() && key(p.intent).equals(entry.getKey())))
        plans.add(
            new Plan(
                "gap" + (++id),
                original,
                new KnowledgeIntentVO(
                    entry.getValue().getBrands(),
                    entry.getValue().getCategories(),
                    entry.getValue().getScope(),
                    true,
                    "原问题中的品牌/类别被遗漏，请分别继续检索。"),
                List.of()));
    if (plans.size() > 30) throw new IllegalArgumentException("知识分组过多，请缩小问题范围");
    return List.copyOf(plans);
  }

  private String constraintIssue(
      String original,
      String sub,
      KnowledgeIntentVO intent,
      boolean explicitHttpFilter,
      List<String> references) {
    var brands = intents.brands(original);
    var selected = intents.brands(sub);
    if (selected.isEmpty() && !intent.isClarificationRequired()) selected = intent.getBrands();
    if (brands.isEmpty()
        && !selected.isEmpty()
        && !selected.equals(List.of("PLATFORM"))
        && references.isEmpty()
        && !explicitHttpFilter) return "原问题和可追溯上下文没有这个品牌，请先确认品牌，不能猜测。";
    if (!brands.isEmpty()
        && !brands.containsAll(selected)
        && !(selected.equals(List.of("PLATFORM")) && intents.platform(original)))
      return "子问题增加了原问题未提到的品牌，请保留原始诉求。";
    if (intents.platform(original) && !intents.platform(sub)) return "原问题涉及本站购买，子问题不能切换为品牌官方渠道。";
    if (intents.outsideSupportedRegion(original)) return "原问题地区尚未收录，请确认购买地区，不能删去地区后套用中国大陆规则。";
    if (!KnowledgeText.identifiers(original).containsAll(KnowledgeText.identifiers(sub)))
      return "子问题新增了原问题没有的数字或型号，请保留原始条件。";
    for (String source : clauses(original)) {
      if (!intents.brands(source).isEmpty()
          && !selected.isEmpty()
          && java.util.Collections.disjoint(intents.brands(source), selected)) continue;
      if (intents.brands(source).size() > 1) continue;
      for (String marker : List.of("不能", "不支持", "未激活", "已激活", "未拆封", "已拆封", "官网", "官方"))
        if (source.contains(marker) && !sub.contains(marker)) return "子问题遗漏了原问题中的渠道、否定或商品状态，请保留条件。";
    }
    return null;
  }

  /** 有明确逐项配对才拆旧的单 query；“苹果与耐克退货”保持整体后按同一类别分别查。 */
  public List<String> clauses(String text) {
    var pieces = List.of(text.split("[，,；;。、]|以及|并且|同时|还有|和|与|及"));
    var meaningful = pieces.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    // 实时订单、商品查询等片段不进入知识需求；但“Apple 和 Nike退货”的省略品牌不能被丢弃。
    if (meaningful.size() > 1) {
      boolean bareBrand =
          meaningful.stream()
              .anyMatch(s -> !intents.brands(s).isEmpty() && intents.categories(s).isEmpty());
      var policy = meaningful.stream().filter(s -> !intents.categories(s).isEmpty()).toList();
      if (!bareBrand
          && !policy.isEmpty()
          && policy.stream()
              .allMatch(
                  s ->
                      !intents.brands(s).isEmpty()
                          || intents.platform(s)
                          || intents.unknownBrand(s))) return policy;
    }
    return List.of(text);
  }

  private String resolveReference(String query, List<String> history, List<String> refs) {
    if (!intents.brands(query).isEmpty()
        || intents.platform(query)
        || !query.matches(".*(它|这款|这个商品|那款).*")) return query;
    var candidates = new LinkedHashMap<String, String>();
    for (String text : history) {
      if (!text.startsWith("user:")) continue;
      for (String brand : intents.brands(text)) candidates.put(brand, text);
    }
    if (candidates.size() == 1) {
      var e = candidates.entrySet().iterator().next();
      refs.add(e.getKey() + " <- " + e.getValue());
      return e.getKey() + " " + query;
    }
    return query + "（品牌与购买渠道尚不明确，请补充品牌）";
  }

  public static String key(KnowledgeIntentVO i) {
    return String.join(",", i.getBrands()) + "/" + String.join(",", i.getCategories());
  }

  /** 不可变的单组检索计划，只在知识模块内部使用。 */
  public static final class Plan {
    private final String id, query;
    private final KnowledgeIntentVO intent;
    private final List<String> references;

    public Plan(String id, String query, KnowledgeIntentVO intent, List<String> references) {
      this.id = id;
      this.query = query;
      this.intent = intent;
      this.references = List.copyOf(references);
    }

    public String getId() {
      return id;
    }

    public String getQuery() {
      return query;
    }

    public KnowledgeIntentVO getIntent() {
      return intent;
    }

    public List<String> getReferences() {
      return references;
    }
  }
}
