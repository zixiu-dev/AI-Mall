package com.bitmall.agent.agent.vo;

import com.bitmall.agent.agent.dao.entity.AgentTurnSummaryDO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Getter;

/** 后台轮次列表使用的安全摘要。 */
@Getter
public final class AgentTurnSummaryVO {
  private final long turnId;
  private final String conversationId;
  private final String status;
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private final LocalDateTime createdAt;

  public AgentTurnSummaryVO(AgentTurnSummaryDO row) {
    turnId = row.getTurnId();
    conversationId = row.getConversationId();
    status = row.getStatus();
    createdAt = row.getCreatedAt();
  }
}
