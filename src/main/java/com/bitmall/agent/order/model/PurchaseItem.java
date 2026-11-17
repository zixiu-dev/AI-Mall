package com.bitmall.agent.order.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

/** 页面确认的 SKU 和数量。购物车正式提交时携带它，避免同金额的购物车变化被悄悄接受。 */
public final class PurchaseItem {
  @Positive private final long skuId;

  @Min(1)
  @Max(20)
  private final int quantity;

  @JsonCreator
  public PurchaseItem(@JsonProperty("skuId") long skuId, @JsonProperty("quantity") int quantity) {
    this.skuId = skuId;
    this.quantity = quantity;
  }

  public long getSkuId() {
    return skuId;
  }

  public int getQuantity() {
    return quantity;
  }
}
