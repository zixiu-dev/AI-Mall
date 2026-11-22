package com.bitmall.agent.product.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 页面和 Agent 共用的商品搜索条件。 构造方法先规范化文本，再校验预算、排序和分页，防止非法条件进入 SQL；from 方法把工具参数转换成同一种请求对象。 */
@Getter
@EqualsAndHashCode
@ToString
public final class ProductSearchRequest {
    /** 搜索关键词，不包含整句自然语言指令。 */
    private final String query;

    private final String category;

    private final String brand;

    /** 最低商品单价，按券前价格筛选。 */
    private final BigDecimal minPrice;

    /** 最高商品单价，按券前价格筛选。 */
    private final BigDecimal maxPrice;

    private final String color;

    private final String size;

    private final String material;

    private final String useCase;

    /** 是否只搜索有库存的规格。 */
    private final boolean inStock;

    /** 排序方式，必须通过后端白名单校验。 */
    private final String sort;

    /** 页码，从 1 开始。 */
    private final int page;

    /** 每页最多返回多少个结果。 */
    private final int limit;

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
        if (!Set.of("recommended", "sales", "priceAsc", "priceDesc", "rating").contains(sort)) {
            throw new IllegalArgumentException("不支持的排序方式");
        }
        if (page < 1 || page > 10000 || limit < 1 || limit > 30) {
            throw new IllegalArgumentException("page 必须为 1~10000，limit 必须为 1~30");
        }
        for (BigDecimal price : new BigDecimal[] {minPrice, maxPrice}) {
            if (price != null
                    && (price.signum() < 0
                            || price.compareTo(new BigDecimal("100000000")) > 0
                            || price.stripTrailingZeros().scale() > 2)) {
                throw new IllegalArgumentException("价格必须为非负金额，最多两位小数且不超过一亿元");
            }
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("最低价格不能高于最高价格");
        }
        if (size != null) {
            size = size.replace("码", "").trim();
        }
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

    /** 把空白条件视为未填写，同时限制条件长度并清理首尾空格。 */
    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > max) {
            throw new IllegalArgumentException("筛选条件过长");
        }
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
