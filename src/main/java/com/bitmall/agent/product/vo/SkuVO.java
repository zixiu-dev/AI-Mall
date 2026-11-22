package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 一种可以实际购买的商品规格。 例如同一款鞋的黑色 42 码和白色 43 码是两个 SKU，各自有价格、属性和库存。 */
@Getter
@EqualsAndHashCode
@ToString
public final class SkuVO {
    /** 规格编号，下单和试算时使用。 */
    private final long id;

    /** 商家定义的规格编码。 */
    private final String skuCode;

    /** 可直接展示给用户的规格文字。 */
    private final String spec;

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    private final BigDecimal originalPrice;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    /** 结构化属性，例如颜色、尺码、材质。 */
    private final Map<String, String> attributes;

    @JsonCreator
    public SkuVO(
            @JsonProperty("id") long id,
            @JsonProperty("skuCode") String skuCode,
            @JsonProperty("spec") String spec,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("originalPrice") BigDecimal originalPrice,
            @JsonProperty("stock") int stock,
            @JsonProperty("attributes") Map<String, String> attributes) {
        this.id = id;
        this.skuCode = skuCode;
        this.spec = spec;
        this.price = price;
        this.originalPrice = originalPrice;
        this.stock = stock;
        this.attributes = attributes;
    }

    /**
     * 通过构造方法明确接收字段。 @JsonCreator 和 @JsonProperty 告诉 Jackson 如何把 JSON 字段传进构造方法；业务参数的校验和默认值处理在赋值前完成。
     */
    public SkuVO(
            long id,
            String skuCode,
            String spec,
            BigDecimal price,
            BigDecimal originalPrice,
            int stock) {
        this(id, skuCode, spec, price, originalPrice, stock, Map.of());
    }
}
