package com.bitmall.agent.aftersales.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** after_sales 表的可写持久化对象，不直接作为接口返回。 */
@TableName("after_sales")
public class AfterSalesEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("order_id")
  private String orderId;

  @TableField("user_id")
  private Long userId;

  @TableField("type")
  private String type;

  @TableField("reason")
  private String reason;

  @TableField("status")
  private String status;

  @TableField("refund_amount")
  private java.math.BigDecimal refundAmount;

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

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public java.math.BigDecimal getRefundAmount() {
    return refundAmount;
  }

  public void setRefundAmount(java.math.BigDecimal refundAmount) {
    this.refundAmount = refundAmount;
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
