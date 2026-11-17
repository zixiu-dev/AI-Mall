package com.bitmall.agent.product.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/** 商品列表卡片所需的数据。 页面展示名称、价格、评分和库存，不包含完整 SKU 列表；查看具体规格需要 ProductDetailVO。 */
public final class ProductCardVO {
    /** 商品编号，查看详情时使用。 */
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

    /** 商品或规格的当前单价。 */
    private final BigDecimal price;

    /** 展示用的原价。 */
    private final BigDecimal originalPrice;

    /** 商品展示评分。 */
    private final BigDecimal rating;

    /** 商品展示销量。 */
    private final int salesCount;

    /** 当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    private final int stock;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取商品编号，查看详情时使用。 */
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

    /** 获取商品或规格的当前单价。 */
    public BigDecimal getPrice() {
        return price;
    }

    /** 获取展示用的原价。 */
    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    /** 获取商品展示评分。 */
    public BigDecimal getRating() {
        return rating;
    }

    /** 获取商品展示销量。 */
    public int getSalesCount() {
        return salesCount;
    }

    /** 获取当前可售库存；搜索结果中表示匹配规格的库存合计。 */
    public int getStock() {
        return stock;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ProductCardVO that = (ProductCardVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(category, that.category)
                && Objects.equals(brand, that.brand)
                && Objects.equals(summary, that.summary)
                && Objects.equals(imageKey, that.imageKey)
                && Objects.equals(price, that.price)
                && Objects.equals(originalPrice, that.originalPrice)
                && Objects.equals(rating, that.rating)
                && Objects.equals(salesCount, that.salesCount)
                && Objects.equals(stock, that.stock);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                category,
                brand,
                summary,
                imageKey,
                price,
                originalPrice,
                rating,
                salesCount,
                stock);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ProductCardVO["
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
                + ", price="
                + price
                + ", originalPrice="
                + originalPrice
                + ", rating="
                + rating
                + ", salesCount="
                + salesCount
                + ", stock="
                + stock
                + "]";
    }
}
