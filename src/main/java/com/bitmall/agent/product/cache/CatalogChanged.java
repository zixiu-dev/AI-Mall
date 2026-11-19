package com.bitmall.agent.product.cache;

import java.util.List;

/** 只携带这笔交易影响的 SKU；监听器在事务提交后删除对应展示库存。 */
public final class CatalogChanged {
  private final List<Long> skuIds;

  public CatalogChanged(List<Long> skuIds) {
    this.skuIds = List.copyOf(skuIds);
  }

  public List<String> stockKeys() {
    return skuIds.stream().distinct().map(id -> "sku:availability:" + id).toList();
  }
}
