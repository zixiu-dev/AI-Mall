package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** pending_action 表的可写持久化对象，不直接作为接口返回。 */
@TableName("pending_action")
public class PendingActionEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("idempotency_key")
  private String idempotencyKey;

  @TableField("user_id")
  private Long userId;

  @TableField("conversation_id")
  private String conversationId;

  @TableField("tool_name")
  private String toolName;

  @TableField("arguments")
  private String arguments;

  @TableField("arguments_hash")
  private String argumentsHash;

  @TableField("summary")
  private String summary;

  @TableField("status")
  private String status;

  @TableField("result")
  private String result;

  @TableField("version")
  private Integer version;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

  @TableField("decided_at")
  private java.time.LocalDateTime decidedAt;

  @TableField("executed_at")
  private java.time.LocalDateTime executedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getArguments() {
    return arguments;
  }

  public void setArguments(String arguments) {
    this.arguments = arguments;
  }

  public String getArgumentsHash() {
    return argumentsHash;
  }

  public void setArgumentsHash(String argumentsHash) {
    this.argumentsHash = argumentsHash;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(java.time.LocalDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public java.time.LocalDateTime getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(java.time.LocalDateTime executedAt) {
    this.executedAt = executedAt;
  }
}
