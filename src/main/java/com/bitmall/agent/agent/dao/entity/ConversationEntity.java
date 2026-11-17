package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** conversation 表的可写持久化对象，不直接作为接口返回。 */
@TableName("conversation")
public class ConversationEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("user_id")
  private Long userId;

  @TableField("title")
  private String title;

  @TableField("summary")
  private String summary;

  @TableField("summarized_until_id")
  private Long summarizedUntilId;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

  @TableField("updated_at")
  private java.time.LocalDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public Long getSummarizedUntilId() {
    return summarizedUntilId;
  }

  public void setSummarizedUntilId(Long summarizedUntilId) {
    this.summarizedUntilId = summarizedUntilId;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
