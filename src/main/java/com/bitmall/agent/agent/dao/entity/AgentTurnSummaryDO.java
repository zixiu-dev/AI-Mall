package com.bitmall.agent.agent.dao.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 近期轮次的数据库投影，只取标识、状态和提交时间。 */
@Getter
@Setter
public class AgentTurnSummaryDO {
  private long turnId;
  private String conversationId;
  private String status;
  private LocalDateTime createdAt;
}
