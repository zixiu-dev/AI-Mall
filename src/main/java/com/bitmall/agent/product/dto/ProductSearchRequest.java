package com.bitmall.agent.product.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 页面和 Agent 共用的商品搜索条件。 构造方法先规范化文本，再校验预算、排序和分页，防止非法条件进入 SQL；from 方法把工具参数转换成同一种请求对象。 */
public final class ProductSearchRequest {
    /** 搜索关键词，不包含整句自然语言指令。 */
    private final String query;

    /** 商品或知识文档所属分类。 */
    private final String category;

    /** 商品品牌。 */
    private final String brand;

    /** 最低商品单价，按券前价格筛选。 */
    private final BigDecimal minPrice;

    /** 最高商品单价，按券前价格筛选。 */
    private final BigDecimal maxPrice;

    /** 用户要求的颜色。 */
    private final String color;

    /** 用户要求的尺码。 */
    private final String size;

    /** 用户要求的材质。 */
    private final String material;

    /** 用户要求的主要用途。 */
    private final String useCase;

    /** 是否只搜索有库存的规格。 */
    private final boolean inStock;

    /** 排序方式，必须通过后端白名单校验。 */
    private final String sort;

    /** 页码，从 1 开始。 */
    private final int page;

    /** 每页最多返回多少个结果。 */
    private final int limit;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public ProductSearchRequest(
            @JsonProperty("query") String query,
            @JsonProperty("category") String category,
            @JsonProperty("brand") String brand,
            @JsonProperty("minPrice") BigDecimal minPrice,
            @JsonProperty("maxPrice") BigDecimal maxPrice,
            @JsonProperty("color") String color,
            @JsonProperty("size") String size,
            @JsonProperty("material") String material,
            @JsonProperty("useCase") String useCase,
            @JsonProperty("inStock") boolean inStock,
            @JsonProperty("sort") String sort,
            @JsonProperty("page") int page,
            @JsonProperty("limit") int limit) {
        query = clean(query, 120);
        category = clean(category, 40);
        brand = clean(brand, 60);
        color = clean(color, 40);
        size = clean(size, 40);
        material = clean(material, 60);
        useCase = clean(useCase, 60);
        sort = sort == null || sort.isBlank() ? "recommended" : sort;
        if (!Set.of("recommended", "sales", "priceAsc", "priceDesc", "rating").contains(sort))
            throw new IllegalArgumentException("不支持的排序方式");
        if (page < 1 || page > 10000 || limit < 1 || limit > 30)
            throw new IllegalArgumentException("page 必须为 1~10000，limit 必须为 1~30");
        for (BigDecimal price : new BigDecimal[] {minPrice, maxPrice})
            if (price != null
                    && (price.signum() < 0
                            || price.compareTo(new BigDecimal("100000000")) > 0
                            || price.stripTrailingZeros().scale() > 2))
                throw new IllegalArgumentException("价格必须为非负金额，最多两位小数且不超过一亿元");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("最低价格不能高于最高价格");
        if (size != null) size = size.replace("码", "").trim();
        this.query = query;
        this.category = category;
        this.brand = brand;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.color = color;
        this.size = size;
        this.material = material;
        this.useCase = useCase;
        this.inStock = inStock;
        this.sort = sort;
        this.page = page;
        this.limit = limit;
    }

    /** 获取搜索关键词，不包含整句自然语言指令。 */
    public String getQuery() {
        return query;
    }

    /** 获取商品或知识文档所属分类。 */
    public String getCategory() {
        return category;
    }

    /** 获取商品品牌。 */
    public String getBrand() {
        return brand;
    }

    /** 获取最低商品单价，按券前价格筛选。 */
    public BigDecimal getMinPrice() {
        return minPrice;
    }

    /** 获取最高商品单价，按券前价格筛选。 */
    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    /** 获取用户要求的颜色。 */
    public String getColor() {
        return color;
    }

    /** 获取用户要求的尺码。 */
    public String getSize() {
        return size;
    }

    /** 获取用户要求的材质。 */
    public String getMaterial() {
        return material;
    }

    /** 获取用户要求的主要用途。 */
    public String getUseCase() {
        return useCase;
    }

    /** 获取是否只搜索有库存的规格。 */
    public boolean isInStock() {
        return inStock;
    }

    /** 获取排序方式，必须通过后端白名单校验。 */
    public String getSort() {
        return sort;
    }

    /** 获取页码，从 1 开始。 */
    public int getPage() {
        return page;
    }

    /** 获取每页最多返回多少个结果。 */
    public int getLimit() {
        return limit;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ProductSearchRequest that = (ProductSearchRequest) other;
        return Objects.equals(query, that.query)
                && Objects.equals(category, that.category)
                && Objects.equals(brand, that.brand)
                && Objects.equals(minPrice, that.minPrice)
                && Objects.equals(maxPrice, that.maxPrice)
                && Objects.equals(color, that.color)
                && Objects.equals(size, that.size)
                && Objects.equals(material, that.material)
                && Objects.equals(useCase, that.useCase)
                && Objects.equals(inStock, that.inStock)
                && Objects.equals(sort, that.sort)
                && Objects.equals(page, that.page)
                && Objects.equals(limit, that.limit);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                query, category, brand, minPrice, maxPrice, color, size, material, useCase, inStock,
                sort, page, limit);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ProductSearchRequest["
                + "query="
                + query
                + ", category="
                + category
                + ", brand="
                + brand
                + ", minPrice="
                + minPrice
                + ", maxPrice="
                + maxPrice
                + ", color="
                + color
                + ", size="
                + size
                + ", material="
                + material
                + ", useCase="
                + useCase
                + ", inStock="
                + inStock
                + ", sort="
                + sort
                + ", page="
                + page
                + ", limit="
                + limit
                + "]";
    }

    /** 把空白条件视为未填写，同时限制条件长度并清理首尾空格。 */
    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw new IllegalArgumentException("筛选条件过长");
        return value.trim();
    }

    /** 把工具参数 Map 转换成和页面相同的搜索请求，统一触发构造方法中的校验。 */
    public static ProductSearchRequest from(Map<String, Object> args) {
        return new ProductSearchRequest(
                text(args, "query"),
                text(args, "category"),
                text(args, "brand"),
                decimal(args, "minPrice"),
                decimal(args, "maxPrice"),
                text(args, "color"),
                text(args, "size"),
                text(args, "material"),
                text(args, "useCase"),
                Boolean.TRUE.equals(args.get("inStock")),
                text(args, "sort"),
                integer(args, "page", 1),
                integer(args, "limit", 4));
    }

    /** 从工具参数读取可选文本，字段缺失时返回空。 */
    private static String text(Map<String, Object> a, String key) {
        return a.get(key) == null ? null : a.get(key).toString();
    }

    /** 把可选金额转换为 BigDecimal，避免使用浮点数计算价格。 */
    private static BigDecimal decimal(Map<String, Object> a, String key) {
        return a.get(key) == null ? null : new BigDecimal(a.get(key).toString());
    }

    /** 精确读取分页整数；不能把小数截断后当成合法页码。 */
    private static int integer(Map<String, Object> a, String key, int fallback) {
        return a.get(key) == null
                ? fallback
                : new BigDecimal(a.get(key).toString()).intValueExact();
    }
}
