package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 单个商品的完整展示数据，包含商品属性和全部可见 SKU。 商品编号标识一款商品，SKU 编号标识颜色、尺码等具体规格，二者不能混用。 */
@Getter
@EqualsAndHashCode
@ToString
public final class ProductDetailVO {
    private final long id;

    private final String name;

    private final String category;

    private final String brand;

    private final String summary;

    /** 前端选择商品图片时使用的资源标识。 */
    private final String imageKey;

    private final BigDecimal rating;

    private final int salesCount;

    /** 这个商品的全部可见规格。 */
    private final List<SkuVO> skus;

    /** 结构化属性，例如颜色、尺码、材质。 */
    private final Map<String, String> attributes;

    @JsonCreator
    public ProductDetailVO(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("brand") String brand,
            @JsonProperty("summary") String summary,
            @JsonProperty("imageKey") String imageKey,
            @JsonProperty("rating") BigDecimal rating,
            @JsonProperty("salesCount") int salesCount,
            @JsonProperty("skus") List<SkuVO> skus,
            @JsonProperty("attributes") Map<String, String> attributes) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.summary = summary;
        this.imageKey = imageKey;
        this.rating = rating;
        this.salesCount = salesCount;
        this.skus = skus;
        this.attributes = attributes;
    }

    /**
     * 通过构造方法明确接收字段。 @JsonCreator 和 @JsonProperty 告诉 Jackson 如何把 JSON 字段传进构造方法；业务参数的校验和默认值处理在赋值前完成。
     */
    public ProductDetailVO(
            long id,
            String name,
            String category,
            String brand,
            String summary,
            String imageKey,
            BigDecimal rating,
            int salesCount,
            List<SkuVO> skus) {
        this(id, name, category, brand, summary, imageKey, rating, salesCount, skus, Map.of());
    }
}
