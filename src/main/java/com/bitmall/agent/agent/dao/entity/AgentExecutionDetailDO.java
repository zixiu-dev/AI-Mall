package com.bitmall.agent.agent.dao.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 单轮诊断明细的 SQL 投影。模型与工具各写自己的表，查询统一合成时间线。 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecutionDetailDO {
  private long turnId;
  private int sequenceNo;
  private Integer stepNo;
  private String itemType;
  private Integer attemptNo;
  private String candidate;
  private String model;
  private Integer modelCallSequence;
  private String toolCallId;
  private String toolName;
  private Integer executionCount;
  private String actionId;
  private String status;
  private String errorCode;
  private LocalDateTime startedAt;
  private long durationMs;
  private Long firstTextMs;
  private Integer inputTokens;
  private Integer outputTokens;

  /** 模型实际调用单独入表。被跳过的模型候选属于普通时间线事件。 */
  public boolean isModelCall() {
    return "MODEL_CALL".equals(itemType);
  }
}
