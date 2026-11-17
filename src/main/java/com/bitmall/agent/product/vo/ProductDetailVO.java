package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 单个商品的完整展示数据，包含商品属性和全部可见 SKU。 商品编号标识一款商品，SKU 编号标识颜色、尺码等具体规格，二者不能混用。 */
public final class ProductDetailVO {
    /** 商品编号。 */
    private final long id;

    /** 商品展示名称。 */
    private final String name;

    /** 商品或知识文档所属分类。 */
    private final String category;

    /** 商品品牌。 */
    private final String brand;

    /** 供用户阅读的简要说明。 */
    private final String summary;

    /** 前端选择商品图片时使用的资源标识。 */
    private final String imageKey;

    /** 商品展示评分。 */
    private final BigDecimal rating;

    /** 商品展示销量。 */
    private final int salesCount;

    /** 这个商品的全部可见规格。 */
    private final List<SkuVO> skus;

    /** 结构化属性，例如颜色、尺码、材质。 */
    private final Map<String, String> attributes;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取商品编号。 */
    public long getId() {
        return id;
    }

    /** 获取商品展示名称。 */
    public String getName() {
        return name;
    }

    /** 获取商品或知识文档所属分类。 */
    public String getCategory() {
        return category;
    }

    /** 获取商品品牌。 */
    public String getBrand() {
        return brand;
    }

    /** 获取供用户阅读的简要说明。 */
    public String getSummary() {
        return summary;
    }

    /** 获取前端选择商品图片时使用的资源标识。 */
    public String getImageKey() {
        return imageKey;
    }

    /** 获取商品展示评分。 */
    public BigDecimal getRating() {
        return rating;
    }

    /** 获取商品展示销量。 */
    public int getSalesCount() {
        return salesCount;
    }

    /** 获取这个商品的全部可见规格。 */
    public List<SkuVO> getSkus() {
        return skus;
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
        ProductDetailVO that = (ProductDetailVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(category, that.category)
                && Objects.equals(brand, that.brand)
                && Objects.equals(summary, that.summary)
                && Objects.equals(imageKey, that.imageKey)
                && Objects.equals(rating, that.rating)
                && Objects.equals(salesCount, that.salesCount)
                && Objects.equals(skus, that.skus)
                && Objects.equals(attributes, that.attributes);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id, name, category, brand, summary, imageKey, rating, salesCount, skus, attributes);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ProductDetailVO["
                + "id="
                + id
                + ", name="
                + name
                + ", category="
                + category
                + ", brand="
                + brand
                + ", summary="
                + summary
                + ", imageKey="
                + imageKey
                + ", rating="
                + rating
                + ", salesCount="
                + salesCount
                + ", skus="
                + skus
                + ", attributes="
                + attributes
                + "]";
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
