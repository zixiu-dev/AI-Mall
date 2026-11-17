package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** order_event_receipt 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_event_receipt")
public class OrderEventReceiptEntity {
  @TableId(value = "event_id", type = IdType.INPUT)
  private String eventId;

  @TableField("order_id")
  private String orderId;

  @TableField("event_type")
  private String eventType;

  @TableField("user_id")
  private Long userId;

  @TableField("pay_amount")
  private java.math.BigDecimal payAmount;

  @TableField("occurred_at")
  private java.time.LocalDateTime occurredAt;

  @TableField("received_at")
  private java.time.LocalDateTime receivedAt;

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

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public java.math.BigDecimal getPayAmount() {
    return payAmount;
  }

  public void setPayAmount(java.math.BigDecimal payAmount) {
    this.payAmount = payAmount;
  }

  public java.time.LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(java.time.LocalDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }

  public java.time.LocalDateTime getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(java.time.LocalDateTime receivedAt) {
    this.receivedAt = receivedAt;
  }
}
