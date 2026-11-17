package com.bitmall.agent.product.vo;

import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 搜索结果类型的容器。 内部 Hit 表示命中的单个商品，Page 表示一整页结果；这里只定义数据结构，查询逻辑在 ProductSearchMapper。 */
public final class ProductSearchResultVO {
    /**
     * 通过构造方法明确接收字段。 @JsonCreator 和 @JsonProperty 告诉 Jackson 如何把 JSON 字段传进构造方法；业务参数的校验和默认值处理在赋值前完成。
     */
    private ProductSearchResultVO() {}

    /** 一个满足搜索条件的商品，以及用于展示的代表 SKU。 价格、颜色、尺码和库存来自同一套筛选条件，避免把不同规格的数据拼成不存在的可购买组合。 */
    public static final class Hit {
        /** 搜索命中的商品编号。 */
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

        /** 满足本次所有筛选条件的代表 SKU 编号。 */
        private final long matchedSkuId;

        /** 代表 SKU 的规格说明。 */
        private final String matchedSpec;

        /** 这个商品中满足条件的 SKU 数量。 */
        private final int matchedSkuCount;

        /** 关键词匹配得分，用于结果排序。 */
        private final int score;

        /** 结构化属性，例如颜色、尺码、材质。 */
        private final Map<String, String> attributes;

        /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
        @JsonCreator
        public Hit(
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
                @JsonProperty("stock") int stock,
                @JsonProperty("matchedSkuId") long matchedSkuId,
                @JsonProperty("matchedSpec") String matchedSpec,
                @JsonProperty("matchedSkuCount") int matchedSkuCount,
                @JsonProperty("score") int score,
                @JsonProperty("attributes") Map<String, String> attributes) {
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
            this.matchedSkuId = matchedSkuId;
            this.matchedSpec = matchedSpec;
            this.matchedSkuCount = matchedSkuCount;
            this.score = score;
            this.attributes = attributes;
        }

        /** 获取搜索命中的商品编号。 */
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

        /** 获取满足本次所有筛选条件的代表 SKU 编号。 */
        public long getMatchedSkuId() {
            return matchedSkuId;
        }

        /** 获取代表 SKU 的规格说明。 */
        public String getMatchedSpec() {
            return matchedSpec;
        }

        /** 获取这个商品中满足条件的 SKU 数量。 */
        public int getMatchedSkuCount() {
            return matchedSkuCount;
        }

        /** 获取关键词匹配得分，用于结果排序。 */
        public int getScore() {
            return score;
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
            Hit that = (Hit) other;
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
                    && Objects.equals(stock, that.stock)
                    && Objects.equals(matchedSkuId, that.matchedSkuId)
                    && Objects.equals(matchedSpec, that.matchedSpec)
                    && Objects.equals(matchedSkuCount, that.matchedSkuCount)
                    && Objects.equals(score, that.score)
                    && Objects.equals(attributes, that.attributes);
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
                    stock,
                    matchedSkuId,
                    matchedSpec,
                    matchedSkuCount,
                    score,
                    attributes);
        }

        /** 返回字段摘要，供日志和本地调试查看。 */
        @Override
        public String toString() {
            return "Hit["
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
                    + ", matchedSkuId="
                    + matchedSkuId
                    + ", matchedSpec="
                    + matchedSpec
                    + ", matchedSkuCount="
                    + matchedSkuCount
                    + ", score="
                    + score
                    + ", attributes="
                    + attributes
                    + "]";
        }

        /** 把搜索命中项简化成页面商品卡片，只保留卡片展示需要的字段。 */
        public ProductCardVO card() {
            return new ProductCardVO(
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
    }

    /** 商品搜索的一页结果。 除了本页商品，还返回总数、是否有下一页、实际筛选条件和建议，页面与 Agent 都依赖这些信息继续翻页。 */
    public static final class Page {
        /** 本次查询或结算包含的数据项。 */
        private final List<Hit> items;

        /** 匹配的总数量，独立于当前页大小。 */
        private final long total;

        /** 页码，从 1 开始。 */
        private final int page;

        /** 每页最多返回多少个结果。 */
        private final int limit;

        /** 当前页后面是否还有结果。 */
        private final boolean hasMore;

        /** 后端拆分并归一化后的关键词。 */
        private final List<String> keywords;

        /** 本次实际使用的筛选条件，便于展示和继续翻页。 */
        private final ProductSearchRequest filters;

        /** 没有结果或超出页码范围时给出的调整建议。 */
        private final List<String> suggestions;

        /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
        @JsonCreator
        public Page(
                @JsonProperty("items") List<Hit> items,
                @JsonProperty("total") long total,
                @JsonProperty("page") int page,
                @JsonProperty("limit") int limit,
                @JsonProperty("hasMore") boolean hasMore,
                @JsonProperty("keywords") List<String> keywords,
                @JsonProperty("filters") ProductSearchRequest filters,
                @JsonProperty("suggestions") List<String> suggestions) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.limit = limit;
            this.hasMore = hasMore;
            this.keywords = keywords;
            this.filters = filters;
            this.suggestions = suggestions;
        }

        /** 获取本次查询或结算包含的数据项。 */
        public List<Hit> getItems() {
            return items;
        }

        /** 获取匹配的总数量，独立于当前页大小。 */
        public long getTotal() {
            return total;
        }

        /** 获取页码，从 1 开始。 */
        public int getPage() {
            return page;
        }

        /** 获取每页最多返回多少个结果。 */
        public int getLimit() {
            return limit;
        }

        /** 获取当前页后面是否还有结果。 */
        public boolean getHasMore() {
            return hasMore;
        }

        /** 获取后端拆分并归一化后的关键词。 */
        public List<String> getKeywords() {
            return keywords;
        }

        /** 获取本次实际使用的筛选条件，便于展示和继续翻页。 */
        public ProductSearchRequest getFilters() {
            return filters;
        }

        /** 获取没有结果或超出页码范围时给出的调整建议。 */
        public List<String> getSuggestions() {
            return suggestions;
        }

        /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Page that = (Page) other;
            return Objects.equals(items, that.items)
                    && Objects.equals(total, that.total)
                    && Objects.equals(page, that.page)
                    && Objects.equals(limit, that.limit)
                    && Objects.equals(hasMore, that.hasMore)
                    && Objects.equals(keywords, that.keywords)
                    && Objects.equals(filters, that.filters)
                    && Objects.equals(suggestions, that.suggestions);
        }

        /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
        @Override
        public int hashCode() {
            return Objects.hash(items, total, page, limit, hasMore, keywords, filters, suggestions);
        }

        /** 返回字段摘要，供日志和本地调试查看。 */
        @Override
        public String toString() {
            return "Page["
                    + "items="
                    + items
                    + ", total="
                    + total
                    + ", page="
                    + page
                    + ", limit="
                    + limit
                    + ", hasMore="
                    + hasMore
                    + ", keywords="
                    + keywords
                    + ", filters="
                    + filters
                    + ", suggestions="
                    + suggestions
                    + "]";
        }
    }
}
