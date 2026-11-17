package com.bitmall.agent.cart.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/** 购物车中一项商品的查询结果。 包含规格、单价、数量、库存和是否勾选；subtotal 只计算本项商品金额，优惠与运费由结算服务统一计算。 */
public final class CartItemVO {
    /** 这条数据的唯一编号。 */
    private final long id;

    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    /** 商品编号；同一个商品可以有多个 SKU。 */
    private final long productId;

    /** 商品名称。 */
    private final String productName;

    /** 订单或购物车中的规格说明。 */
    private final String skuSpec;

    /** 前端选择商品图片时使用的资源标识。 */
    private final String imageKey;

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    /** 购买数量。 */
    private final int quantity;

    /** 购物车中是否勾选此项参与结算。 */
    private final boolean selected;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取这条数据的唯一编号。 */
    public long getId() {
        return id;
    }

    /** 获取具体商品规格的编号，不等于商品编号。 */
    public long getSkuId() {
        return skuId;
    }

    /** 获取商品编号；同一个商品可以有多个 SKU。 */
    public long getProductId() {
        return productId;
    }

    /** 获取商品名称。 */
    public String getProductName() {
        return productName;
    }

    /** 获取订单或购物车中的规格说明。 */
    public String getSkuSpec() {
        return skuSpec;
    }

    /** 获取前端选择商品图片时使用的资源标识。 */
    public String getImageKey() {
        return imageKey;
    }

    /** 获取商品或规格的当前单价。 */
    public BigDecimal getPrice() {
        return price;
    }

    /** 获取当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    public int getStock() {
        return stock;
    }

    /** 获取购买数量。 */
    public int getQuantity() {
        return quantity;
    }

    /** 获取购物车中是否勾选此项参与结算。 */
    public boolean isSelected() {
        return selected;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CartItemVO that = (CartItemVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(skuId, that.skuId)
                && Objects.equals(productId, that.productId)
                && Objects.equals(productName, that.productName)
                && Objects.equals(skuSpec, that.skuSpec)
                && Objects.equals(imageKey, that.imageKey)
                && Objects.equals(price, that.price)
                && Objects.equals(stock, that.stock)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(selected, that.selected);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                skuId,
                productId,
                productName,
                skuSpec,
                imageKey,
                price,
                stock,
                quantity,
                selected);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CartItemVO["
                + "id="
                + id
                + ", skuId="
                + skuId
                + ", productId="
                + productId
                + ", productName="
                + productName
                + ", skuSpec="
                + skuSpec
                + ", imageKey="
                + imageKey
                + ", price="
                + price
                + ", stock="
                + stock
                + ", quantity="
                + quantity
                + ", selected="
                + selected
                + "]";
    }

    /** 用当前单价乘以数量，得到这一项的商品金额；不计算优惠和运费。 */
    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
