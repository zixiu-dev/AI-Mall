package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** user_memory 表的可写持久化对象，不直接作为接口返回。 */
@TableName("user_memory")
public class MemoryEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("user_id")
  private Long userId;

  @TableField("memory_key")
  private String memoryKey;

  @TableField("memory_value")
  private String memoryValue;

  @TableField("source_conversation_id")
  private String sourceConversationId;

  @TableField("version")
  private Integer version;

  @TableField("updated_at")
  private java.time.LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getMemoryKey() {
    return memoryKey;
  }

  public void setMemoryKey(String memoryKey) {
    this.memoryKey = memoryKey;
  }

  public String getMemoryValue() {
    return memoryValue;
  }

  public void setMemoryValue(String memoryValue) {
    this.memoryValue = memoryValue;
  }

  public String getSourceConversationId() {
    return sourceConversationId;
  }

  public void setSourceConversationId(String sourceConversationId) {
    this.sourceConversationId = sourceConversationId;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public java.time.LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
