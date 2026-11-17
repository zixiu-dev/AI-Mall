package com.bitmall.agent.agent.dao.entity;

/** 消息插入参数，MyBatis 将数据库生成的主键回填到 id。 */
public class ChatMessageEntity {
  private Long id;
  private String conversationId;
  private String role;
  private String content;
  private String metadata;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String value) {
    conversationId = value;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    role = value;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String value) {
    metadata = value;
  }
}
