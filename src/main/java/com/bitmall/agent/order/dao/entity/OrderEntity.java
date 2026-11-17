package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** customer_order 表的可写持久化对象，不直接作为接口返回。 */
@TableName("customer_order")
public class OrderEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("user_id")
  private Long userId;

  @TableField("status")
  private String status;

  @TableField("goods_amount")
  private java.math.BigDecimal goodsAmount;

  @TableField("discount_amount")
  private java.math.BigDecimal discountAmount;

  @TableField("freight_amount")
  private java.math.BigDecimal freightAmount;

  @TableField("pay_amount")
  private java.math.BigDecimal payAmount;

  @TableField("address_snapshot")
  private String addressSnapshot;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

  @TableField("paid_at")
  private java.time.LocalDateTime paidAt;

  @TableField("cancelled_at")
  private java.time.LocalDateTime cancelledAt;

  @TableField("version")
  private Integer version;

  @TableField("submit_trace_id")
  private String submitTraceId;

  @TableField("coupon_id")
  private Long couponId;

  @TableField("coupon_name")
  private String couponName;

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public java.math.BigDecimal getGoodsAmount() {
    return goodsAmount;
  }

  public void setGoodsAmount(java.math.BigDecimal goodsAmount) {
    this.goodsAmount = goodsAmount;
  }

  public java.math.BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public void setDiscountAmount(java.math.BigDecimal discountAmount) {
    this.discountAmount = discountAmount;
  }

  public java.math.BigDecimal getFreightAmount() {
    return freightAmount;
  }

  public void setFreightAmount(java.math.BigDecimal freightAmount) {
    this.freightAmount = freightAmount;
  }

  public java.math.BigDecimal getPayAmount() {
    return payAmount;
  }

  public void setPayAmount(java.math.BigDecimal payAmount) {
    this.payAmount = payAmount;
  }

  public String getAddressSnapshot() {
    return addressSnapshot;
  }

  public void setAddressSnapshot(String addressSnapshot) {
    this.addressSnapshot = addressSnapshot;
  }

  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public java.time.LocalDateTime getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(java.time.LocalDateTime paidAt) {
    this.paidAt = paidAt;
  }

  public java.time.LocalDateTime getCancelledAt() {
    return cancelledAt;
  }

  public void setCancelledAt(java.time.LocalDateTime cancelledAt) {
    this.cancelledAt = cancelledAt;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getSubmitTraceId() {
    return submitTraceId;
  }

  public void setSubmitTraceId(String submitTraceId) {
    this.submitTraceId = submitTraceId;
  }

  public Long getCouponId() {
    return couponId;
  }

  public void setCouponId(Long couponId) {
    this.couponId = couponId;
  }

  public String getCouponName() {
    return couponName;
  }

  public void setCouponName(String couponName) {
    this.couponName = couponName;
  }
}
