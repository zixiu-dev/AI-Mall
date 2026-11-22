package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 商品列表卡片所需的数据。 页面展示名称、价格、评分和库存，不包含完整 SKU 列表；查看具体规格需要 ProductDetailVO。 */
@Getter
@EqualsAndHashCode
@ToString
public final class ProductCardVO {
    /** 商品编号，查看详情时使用。 */
    private final long id;

    private final String name;

    private final String category;

    private final String brand;

    private final String summary;

    /** 前端选择商品图片时使用的资源标识。 */
    private final String imageKey;

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    private final BigDecimal originalPrice;

    private final BigDecimal rating;

    private final int salesCount;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    @JsonCreator
    public ProductCardVO(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("brand") String brand,
            @JsonProperty("summary") String summary,
            @JsonProperty("imageKey") String imageKey,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("originalPrice") BigDecimal originalPrice,
            @JsonProperty("rating") BigDecimal rating,
            @JsonProperty("salesCount") int salesCount,
            @JsonProperty("stock") int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.summary = summary;
        this.imageKey = imageKey;
        this.price = price;
        this.originalPrice = originalPrice;
        this.rating = rating;
        this.salesCount = salesCount;
        this.stock = stock;
    }
}
