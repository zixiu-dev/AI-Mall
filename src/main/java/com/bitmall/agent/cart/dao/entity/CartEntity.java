package com.bitmall.agent.cart.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** cart_item 表的可写持久化对象，不直接作为接口返回。 */
@TableName("cart_item")
public class CartEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("user_id")
  private Long userId;

  @TableField("sku_id")
  private Long skuId;

  @TableField("quantity")
  private Integer quantity;

  @TableField("selected")
  private Boolean selected;

  @TableField("created_at")
  private java.time.LocalDateTime createdAt;

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

  public Long getSkuId() {
    return skuId;
  }

  public void setSkuId(Long skuId) {
    this.skuId = skuId;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public Boolean getSelected() {
    return selected;
  }

  public void setSelected(Boolean selected) {
    this.selected = selected;
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
