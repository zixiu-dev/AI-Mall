package com.bitmall.agent.knowledge.vo;

import java.util.List;
import lombok.Getter;

/** 覆盖只表示哪些知识需求有保留证据，不是答案正确率或业务成功率。 */
@Getter
public final class KnowledgeCoverageVO {
  private final int requested;
  private final int answeredWithEvidence;
  private final List<String> missingQueryIds;

  public KnowledgeCoverageVO(
      int requested, int answeredWithEvidence, List<String> missingQueryIds) {
    this.requested = requested;
    this.answeredWithEvidence = answeredWithEvidence;
    this.missingQueryIds = List.copyOf(missingQueryIds);
  }
}
