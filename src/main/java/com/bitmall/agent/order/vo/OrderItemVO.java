package com.bitmall.agent.order.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/** 订单中的一项商品快照。 保存下单时的名称、规格、单价和数量，历史订单不会直接使用商品当前价格重新计算。 */
public final class OrderItemVO {
    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    /** 商品名称。 */
    private final String productName;

    /** 订单或购物车中的规格说明。 */
    private final String skuSpec;

    /** 下单时保存的商品单价。 */
    private final BigDecimal unitPrice;

    /** 购买数量。 */
    private final int quantity;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取具体商品规格的编号，不等于商品编号。 */
    public long getSkuId() {
        return skuId;
    }

    /** 获取商品名称。 */
    public String getProductName() {
        return productName;
    }

    /** 获取订单或购物车中的规格说明。 */
    public String getSkuSpec() {
        return skuSpec;
    }

    /** 获取下单时保存的商品单价。 */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** 获取购买数量。 */
    public int getQuantity() {
        return quantity;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        OrderItemVO that = (OrderItemVO) other;
        return Objects.equals(skuId, that.skuId)
                && Objects.equals(productName, that.productName)
                && Objects.equals(skuSpec, that.skuSpec)
                && Objects.equals(unitPrice, that.unitPrice)
                && Objects.equals(quantity, that.quantity);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(skuId, productName, skuSpec, unitPrice, quantity);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "OrderItemVO["
                + "skuId="
                + skuId
                + ", productName="
                + productName
                + ", skuSpec="
                + skuSpec
                + ", unitPrice="
                + unitPrice
                + ", quantity="
                + quantity
                + "]";
    }
}
