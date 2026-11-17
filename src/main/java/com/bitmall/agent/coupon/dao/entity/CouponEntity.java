package com.bitmall.agent.coupon.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** coupon 表的可写持久化对象，不直接作为接口返回。 */
@TableName("coupon")
public class CouponEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("name")
  private String name;

  @TableField("threshold_amount")
  private java.math.BigDecimal thresholdAmount;

  @TableField("discount_amount")
  private java.math.BigDecimal discountAmount;

  @TableField("total_stock")
  private Integer totalStock;

  @TableField("claimed_count")
  private Integer claimedCount;

  @TableField("start_at")
  private java.time.LocalDateTime startAt;

  @TableField("end_at")
  private java.time.LocalDateTime endAt;

  @TableField("status")
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public java.math.BigDecimal getThresholdAmount() {
    return thresholdAmount;
  }

  public void setThresholdAmount(java.math.BigDecimal thresholdAmount) {
    this.thresholdAmount = thresholdAmount;
  }

  public java.math.BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public void setDiscountAmount(java.math.BigDecimal discountAmount) {
    this.discountAmount = discountAmount;
  }

  public Integer getTotalStock() {
    return totalStock;
  }

  public void setTotalStock(Integer totalStock) {
    this.totalStock = totalStock;
  }

  public Integer getClaimedCount() {
    return claimedCount;
  }

  public void setClaimedCount(Integer claimedCount) {
    this.claimedCount = claimedCount;
  }

  public java.time.LocalDateTime getStartAt() {
    return startAt;
  }

  public void setStartAt(java.time.LocalDateTime startAt) {
    this.startAt = startAt;
  }

  public java.time.LocalDateTime getEndAt() {
    return endAt;
  }

  public void setEndAt(java.time.LocalDateTime endAt) {
    this.endAt = endAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
