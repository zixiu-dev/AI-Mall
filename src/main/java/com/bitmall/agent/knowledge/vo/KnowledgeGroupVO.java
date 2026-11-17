package com.bitmall.agent.knowledge.vo;

import java.util.List;

/** 一个子问题的真实证据及独立状态；不把不同品牌/类别的结果拼成统一政策。 */
public final class KnowledgeGroupVO {
  private final String queryId, query, status, message, retrievalMode;
  private final KnowledgeIntentVO intent;
  private final List<KnowledgeHitVO> hits;
  private final List<String> warnings, variants, resolvedContextReferences;

  public KnowledgeGroupVO(
      String queryId,
      String query,
      String status,
      String message,
      String retrievalMode,
      KnowledgeIntentVO intent,
      List<KnowledgeHitVO> hits,
      List<String> warnings,
      List<String> variants,
      List<String> resolvedContextReferences) {
    this.queryId = queryId;
    this.query = query;
    this.status = status;
    this.message = message;
    this.retrievalMode = retrievalMode;
    this.intent = intent;
    this.hits = List.copyOf(hits);
    this.warnings = List.copyOf(warnings);
    this.variants = List.copyOf(variants);
    this.resolvedContextReferences = List.copyOf(resolvedContextReferences);
  }

  public String getQueryId() {
    return queryId;
  }

  public String getQuery() {
    return query;
  }

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public String getRetrievalMode() {
    return retrievalMode;
  }

  public KnowledgeIntentVO getIntent() {
    return intent;
  }

  public List<KnowledgeHitVO> getHits() {
    return hits;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public List<String> getVariants() {
    return variants;
  }

  public List<String> getResolvedContextReferences() {
    return resolvedContextReferences;
  }
}
