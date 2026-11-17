package com.bitmall.agent.knowledge.vo;

import java.util.List;

/** 检索回执：明确区分找到文档、没有依据与需要补问，防止空结果被模型当作政策允许。 */
public final class KnowledgeSearchResultVO {
  private final String status;
  private final KnowledgeIntentVO intent;
  private final String message;
  private final List<KnowledgeHitVO> hits;
  private final List<KnowledgeGroupVO> groups;
  private final KnowledgeCoverageVO coverage;
  private final List<String> warnings;
  private final String retrievalMode;

  public KnowledgeSearchResultVO(
      String status, KnowledgeIntentVO intent, String message, List<KnowledgeHitVO> hits) {
    this.status = status;
    this.intent = intent;
    this.message = message;
    this.hits = List.copyOf(hits);
    this.groups = List.of();
    this.coverage =
        new KnowledgeCoverageVO(
            1, hits.isEmpty() ? 0 : 1, hits.isEmpty() ? List.of("q1") : List.of());
    this.warnings = List.of();
    this.retrievalMode = "DB_KEYWORD";
  }

  /** 聚合每个知识需求，不将工具正常返回误判为全部内容已覆盖。 */
  public KnowledgeSearchResultVO(List<KnowledgeGroupVO> groups, List<String> warnings) {
    this.groups = List.copyOf(groups);
    this.warnings = List.copyOf(warnings);
    var unique = new java.util.LinkedHashMap<String, KnowledgeHitVO>();
    for (var group : groups)
      for (var hit : group.getHits()) unique.putIfAbsent(hit.evidenceKey(), hit);
    this.hits = List.copyOf(unique.values());
    var missing =
        groups.stream()
            .filter(g -> !"FOUND".equals(g.getStatus()) || g.getHits().isEmpty())
            .map(KnowledgeGroupVO::getQueryId)
            .toList();
    this.coverage =
        new KnowledgeCoverageVO(
            groups.size(),
            (int) groups.stream().filter(g -> !g.getHits().isEmpty()).count(),
            missing);
    this.status =
        missing.isEmpty()
            ? "FOUND"
            : (!hits.isEmpty()
                    || groups.stream()
                        .anyMatch(g -> List.of("PARTIAL", "ERROR").contains(g.getStatus())))
                ? "PARTIAL"
                : groups.stream().anyMatch(g -> "CLARIFICATION_REQUIRED".equals(g.getStatus()))
                    ? "CLARIFICATION_REQUIRED"
                    : "NO_MATCH";
    var brands =
        groups.stream()
            .filter(g -> g.getIntent() != null)
            .flatMap(g -> g.getIntent().getBrands().stream())
            .distinct()
            .toList();
    var types =
        groups.stream()
            .filter(g -> g.getIntent() != null)
            .flatMap(g -> g.getIntent().getCategories().stream())
            .distinct()
            .toList();
    this.intent =
        new KnowledgeIntentVO(
            brands,
            types,
            groups.stream()
                            .map(
                                g ->
                                    g.getIntent() == null ? "UNRESOLVED" : g.getIntent().getScope())
                            .distinct()
                            .count()
                        == 1
                    && !groups.isEmpty()
                ? (groups.get(0).getIntent() == null
                    ? "UNRESOLVED"
                    : groups.get(0).getIntent().getScope())
                : "MIXED",
            status.equals("CLARIFICATION_REQUIRED"),
            "按组核对购买渠道和资料缺口");
    this.message =
        "FOUND".equals(status)
            ? "全部知识组已有资料；仍须核对适用渠道、条件和例外。"
            : "PARTIAL".equals(status)
                ? "部分需求没有完整保留的证据：" + String.join("、", missing)
                : groups.stream()
                    .map(KnowledgeGroupVO::getMessage)
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("；"));
    this.retrievalMode =
        groups.stream()
            .flatMap(g -> java.util.Arrays.stream(g.getRetrievalMode().split("\\+")))
            .filter(mode -> !"NONE".equals(mode))
            .distinct()
            .collect(java.util.stream.Collectors.joining("+"));
  }

  public List<KnowledgeGroupVO> getGroups() {
    return groups;
  }

  public KnowledgeCoverageVO getCoverage() {
    return coverage;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public String getRetrievalMode() {
    return retrievalMode;
  }

  public String getStatus() {
    return status;
  }

  public KnowledgeIntentVO getIntent() {
    return intent;
  }

  public String getMessage() {
    return message;
  }

  public List<KnowledgeHitVO> getHits() {
    return hits;
  }
}
