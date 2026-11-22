package com.bitmall.agent.knowledge.vo;

import java.util.List;
import lombok.Getter;

/** 一个子问题的真实证据及独立状态；不把不同品牌/类别的结果拼成统一政策。 */
@Getter
public final class KnowledgeGroupVO {
  private final String queryId;
  private final String query;
  private final String status;
  private final String message;
  private final String retrievalMode;
  private final KnowledgeIntentVO intent;
  private final List<KnowledgeHitVO> hits;
  private final List<String> warnings;
  private final List<String> variants;
  private final List<String> resolvedContextReferences;

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
}
