package com.bitmall.agent.order.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 订单中的一项商品快照。 保存下单时的名称、规格、单价和数量，历史订单不会直接使用商品当前价格重新计算。 */
@Getter
@EqualsAndHashCode
@ToString
public final class OrderItemVO {
    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    private final String productName;

    private final String skuSpec;

    /** 下单时保存的商品单价。 */
    private final BigDecimal unitPrice;

    private final int quantity;

    @JsonCreator
    public OrderItemVO(
            @JsonProperty("skuId") long skuId,
            @JsonProperty("productName") String productName,
            @JsonProperty("skuSpec") String skuSpec,
            @JsonProperty("unitPrice") BigDecimal unitPrice,
            @JsonProperty("quantity") int quantity) {
        this.skuId = skuId;
        this.productName = productName;
        this.skuSpec = skuSpec;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
