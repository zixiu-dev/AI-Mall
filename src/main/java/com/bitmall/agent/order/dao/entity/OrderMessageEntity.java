package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** order_message_outbox 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_message_outbox")
public class OrderMessageEntity {
  @TableId(value = "event_id", type = IdType.INPUT)
  private String eventId;

  @TableField("order_id")
  private String orderId;

  @TableField("topic")
  private String topic;

  @TableField("event_type")
  private String eventType;

  @TableField("payload")
  private String payload;

  /** 定时消息绝对投递时刻；即时消息不填写。 */
  @TableField("deliver_at_epoch_ms")
  private Long deliverAtEpochMillis;

  public Long getDeliverAtEpochMillis() {
    return deliverAtEpochMillis;
  }

  public void setDeliverAtEpochMillis(Long deliverAtEpochMillis) {
    this.deliverAtEpochMillis = deliverAtEpochMillis;
  }

  @TableField("status")
  private String status;

  @TableField("attempts")
  private Integer attempts;

  @TableField("next_attempt_at")
  private java.time.LocalDateTime nextAttemptAt;

  @TableField("lease_token")
  private String leaseToken;

  @TableField("locked_until")
  private java.time.LocalDateTime lockedUntil;

  @TableField("broker_msg_id")
  private String brokerMsgId;

  @TableField("last_error")
  private String lastError;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

  @TableField("sent_at")
  private java.time.LocalDateTime sentAt;

  @TableField("trace_id")
  private String traceId;

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getAttempts() {
    return attempts;
  }

  public void setAttempts(Integer attempts) {
    this.attempts = attempts;
  }

  public java.time.LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(java.time.LocalDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getLeaseToken() {
    return leaseToken;
  }

  public void setLeaseToken(String leaseToken) {
    this.leaseToken = leaseToken;
  }

  public java.time.LocalDateTime getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(java.time.LocalDateTime lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public String getBrokerMsgId() {
    return brokerMsgId;
  }

  public void setBrokerMsgId(String brokerMsgId) {
    this.brokerMsgId = brokerMsgId;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(java.time.LocalDateTime sentAt) {
    this.sentAt = sentAt;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }
}
