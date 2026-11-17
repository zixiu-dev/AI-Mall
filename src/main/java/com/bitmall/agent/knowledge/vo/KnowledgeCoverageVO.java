package com.bitmall.agent.knowledge.vo;

import java.util.List;

/** 覆盖只表示哪些知识需求有保留证据，不是答案正确率或业务成功率。 */
public final class KnowledgeCoverageVO {
  private final int requested, answeredWithEvidence;
  private final List<String> missingQueryIds;

  public KnowledgeCoverageVO(
      int requested, int answeredWithEvidence, List<String> missingQueryIds) {
    this.requested = requested;
    this.answeredWithEvidence = answeredWithEvidence;
    this.missingQueryIds = List.copyOf(missingQueryIds);
  }

  public int getRequested() {
    return requested;
  }

  public int getAnsweredWithEvidence() {
    return answeredWithEvidence;
  }

  public List<String> getMissingQueryIds() {
    return missingQueryIds;
  }
}
