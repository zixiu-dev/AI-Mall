package com.bitmall.agent.product.cache;

import com.bitmall.agent.product.vo.*;
import java.math.BigDecimal;
import java.util.*;

/** 缓存中一款商品的稳定资料；特意没有 SKU 价格和库存字段，不能意外写入旧交易值。 */
public final class ProductProfile {
  public long id;
  public String name, category, brand, summary, imageKey;
  public BigDecimal rating;
  public int salesCount;
  public Map<String, String> attributes;
  public List<Variant> skus;

  /** 规格身份和属性，与本次展示报价独立。 */
  public static final class Variant {
    public long id;
    public String skuCode, spec;
    public Map<String, String> attributes;
  }

  public ProductDetailVO withOffers(List<SkuVO> offers) {
    return new ProductDetailVO(
        id, name, category, brand, summary, imageKey, rating, salesCount, offers, attributes);
  }
}
