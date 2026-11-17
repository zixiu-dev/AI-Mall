package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** order_submit_trace 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_submit_trace")
public class OrderTraceEntity {
  @TableId(value = "trace_id", type = IdType.INPUT)
  private String traceId;

  @TableField("user_id")
  private Long userId;

  @TableField("origin_key")
  private String originKey;

  @TableField("mode")
  private String mode;

  @TableField("request_snapshot")
  private String requestSnapshot;

  @TableField("request_hash")
  private String requestHash;

  @TableField("status")
  private String status;

  @TableField("order_id")
  private String orderId;

  @TableField("result_json")
  private String resultJson;

  @TableField("failure_code")
  private String failureCode;

  @TableField("failure_reason")
  private String failureReason;

  @TableField("version")
  private Integer version;

  @TableField("expires_at")
  private java.time.LocalDateTime expiresAt;

  @TableField("next_check_at")
  private java.time.LocalDateTime nextCheckAt;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

  /** 异步受理的最长处理期限。 */
  @TableField("processing_expires_at")
  private java.time.LocalDateTime processingExpiresAt;

  @TableField("accepted_at")
  private java.time.LocalDateTime acceptedAt;

  @TableField("updated_at")
  private java.time.LocalDateTime updatedAt;

  @TableField("finished_at")
  private java.time.LocalDateTime finishedAt;

  public java.time.LocalDateTime getProcessingExpiresAt() {
    return processingExpiresAt;
  }

  public void setProcessingExpiresAt(java.time.LocalDateTime value) {
    this.processingExpiresAt = value;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getOriginKey() {
    return originKey;
  }

  public void setOriginKey(String originKey) {
    this.originKey = originKey;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getRequestSnapshot() {
    return requestSnapshot;
  }

  public void setRequestSnapshot(String requestSnapshot) {
    this.requestSnapshot = requestSnapshot;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public void setFailureCode(String failureCode) {
    this.failureCode = failureCode;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public java.time.LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public java.time.LocalDateTime getNextCheckAt() {
    return nextCheckAt;
  }

  public void setNextCheckAt(java.time.LocalDateTime nextCheckAt) {
    this.nextCheckAt = nextCheckAt;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.LocalDateTime getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(java.time.LocalDateTime acceptedAt) {
    this.acceptedAt = acceptedAt;
  }

  public java.time.LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public java.time.LocalDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(java.time.LocalDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }
}
