package com.bitmall.agent.agent.vo;

import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/** 助手消息的排障视图。返回当前已落库的明细，回答状态仍来自原消息。 */
@Getter
public final class AgentExecutionVO {
  private final String conversationId;
  private final long turnId;
  private final long userMessageId;
  private final String status;
  private final List<Entry> entries;

  public AgentExecutionVO(ChatMessageDO message, List<AgentExecutionDetailDO> details) {
    conversationId = message.getConversationId();
    turnId = message.getId();
    userMessageId = message.getReplyToMessageId();
    status = message.getGenerationStatus();
    entries = details.stream().map(Entry::new).toList();
  }

  /** 类型不适用或实际未取得的字段省略，避免把未知 Token 显示成零。 */
  @Getter
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static final class Entry {
    private final int sequenceNo;
    private final String itemType;
    private final Integer stepNo;
    private final Integer attemptNo;
    private final String candidate;
    private final String model;
    private final Integer modelCallSequence;
    private final String toolCallId;
    private final String toolName;
    private final Integer executionCount;
    private final String actionId;
    private final String status;
    private final String errorCode;
    private final LocalDateTime startedAt;
    private final long durationMs;
    private final Long firstTextMs;
    private final Integer inputTokens;
    private final Integer outputTokens;

    private Entry(AgentExecutionDetailDO row) {
      sequenceNo = row.getSequenceNo();
      itemType = row.getItemType();
      stepNo = row.getStepNo();
      attemptNo = row.getAttemptNo();
      candidate = row.getCandidate();
      model = row.getModel();
      modelCallSequence = row.getModelCallSequence();
      toolCallId = row.getToolCallId();
      toolName = row.getToolName();
      executionCount = row.getExecutionCount();
      actionId = row.getActionId();
      status = row.getStatus();
      errorCode = row.getErrorCode();
      startedAt = row.getStartedAt();
      durationMs = row.getDurationMs();
      firstTextMs = row.getFirstTextMs();
      inputTokens = row.getInputTokens();
      outputTokens = row.getOutputTokens();
    }
  }
}
