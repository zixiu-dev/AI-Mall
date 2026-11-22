package com.bitmall.agent.cart.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 购物车中一项商品的查询结果。 包含规格、单价、数量、库存和是否勾选；subtotal 只计算本项商品金额，优惠与运费由结算服务统一计算。 */
@Getter
@EqualsAndHashCode
@ToString
public final class CartItemVO {
    private final long id;

    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    /** 商品编号；同一个商品可以有多个 SKU。 */
    private final long productId;

    private final String productName;

    private final String skuSpec;

    /** 前端选择商品图片时使用的资源标识。 */
    private final String imageKey;

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    private final int quantity;

    /** 购物车中是否勾选此项参与结算。 */
    private final boolean selected;

    @JsonCreator
    public CartItemVO(
            @JsonProperty("id") long id,
            @JsonProperty("skuId") long skuId,
            @JsonProperty("productId") long productId,
            @JsonProperty("productName") String productName,
            @JsonProperty("skuSpec") String skuSpec,
            @JsonProperty("imageKey") String imageKey,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("stock") int stock,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("selected") boolean selected) {
        this.id = id;
        this.skuId = skuId;
        this.productId = productId;
        this.productName = productName;
        this.skuSpec = skuSpec;
        this.imageKey = imageKey;
        this.price = price;
        this.stock = stock;
        this.quantity = quantity;
        this.selected = selected;
    }

    /** 用当前单价乘以数量，得到这一项的商品金额；不计算优惠和运费。 */
    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
