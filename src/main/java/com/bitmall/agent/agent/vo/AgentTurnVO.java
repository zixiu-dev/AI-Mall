package com.bitmall.agent.agent.vo;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import lombok.Getter;

/** 已提交的问题与助手回答编号，生成结束时包含持久化结果。 */
@Getter
public final class AgentTurnVO {
  private final String conversationId;
  private final long userMessageId;
  private final long messageId;
  private final String status;
  private final String content;

  public AgentTurnVO(ChatMessageDO message) {
    conversationId = message.getConversationId();
    userMessageId = message.getReplyToMessageId();
    messageId = message.getId();
    status = message.getGenerationStatus();
    content = message.getContent();
  }
}
