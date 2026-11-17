package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** 一种可以实际购买的商品规格。 例如同一款鞋的黑色 42 码和白色 43 码是两个 SKU，各自有价格、属性和库存。 */
public final class SkuVO {
    /** 规格编号，下单和试算时使用。 */
    private final long id;

    /** 商家定义的规格编码。 */
    private final String skuCode;

    /** 可直接展示给用户的规格文字。 */
    private final String spec;

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    /** 展示用的原价。 */
    private final BigDecimal originalPrice;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    /** 结构化属性，例如颜色、尺码、材质。 */
    private final Map<String, String> attributes;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取规格编号，下单和试算时使用。 */
    public long getId() {
        return id;
    }

    /** 获取商家定义的规格编码。 */
    public String getSkuCode() {
        return skuCode;
    }

    /** 获取可直接展示给用户的规格文字。 */
    public String getSpec() {
        return spec;
    }

    /** 获取商品或规格的当前单价。 */
    public BigDecimal getPrice() {
        return price;
    }

    /** 获取展示用的原价。 */
    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    /** 获取当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    public int getStock() {
        return stock;
    }

    /** 获取结构化属性，例如颜色、尺码、材质。 */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        SkuVO that = (SkuVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(skuCode, that.skuCode)
                && Objects.equals(spec, that.spec)
                && Objects.equals(price, that.price)
                && Objects.equals(originalPrice, that.originalPrice)
                && Objects.equals(stock, that.stock)
                && Objects.equals(attributes, that.attributes);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(id, skuCode, spec, price, originalPrice, stock, attributes);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "SkuVO["
                + "id="
                + id
                + ", skuCode="
                + skuCode
                + ", spec="
                + spec
                + ", price="
                + price
                + ", originalPrice="
                + originalPrice
                + ", stock="
                + stock
                + ", attributes="
                + attributes
                + "]";
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
