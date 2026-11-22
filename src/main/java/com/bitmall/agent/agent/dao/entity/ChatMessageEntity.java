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
}
