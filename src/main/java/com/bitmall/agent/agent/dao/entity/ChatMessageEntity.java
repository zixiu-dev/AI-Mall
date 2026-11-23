package com.bitmall.agent.agent.dao.entity;

import lombok.Getter;
import lombok.Setter;

/** 消息插入参数，MyBatis 将数据库生成的主键回填到 id。 */
@Getter
@Setter
public class ChatMessageEntity {
  private Long id;
  private String conversationId;
  private String role;
  private String content;
  private String metadata;
  /** 仅可恢复的助手回答写入生成状态和原问题编号。 */
  private String generationStatus;
  private Long replyToMessageId;
  /** 用户与请求编号组合，由数据库唯一约束保证重试幂等。 */
  private String requestKey;
}
