package com.bitmall.agent.product.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.product.cache.ProductProfile;
import com.bitmall.agent.product.dao.mapper.ProductSearchMapper;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.vo.ProductSearchResultVO.Hit;
import com.bitmall.agent.product.vo.ProductSearchResultVO.Page;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 结构化商品搜索的数据访问。
 *
 * <p>SQL 片段在 Mapper XML 里固定，用户输入只作为绑定参数传入。价格、颜色、尺码、库存等规格条件都作用在同一个 SKU 上， 避免出现"红色有货、但 42
 * 码才是红色"这种拼出来的不存在组合。先排序再分页，最后批量补属性。
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {
  /** 单次搜索最多允许的关键词数。 */
  private static final int MAX_KEYWORDS = 8;

  /** 用户输入里分隔关键词的字符：空白和中英文逗号、顿号、分号。 */
  private static final String KEYWORD_SEPARATORS = "[\\s,，、;；]+";

  /** 有限的同义词归一：左边是用户可能输入的词，右边是目录里实际使用的词。 */
  private static final Map<String, String> SYNONYMS =
      Map.of(
          "运动鞋", "跑鞋",
          "慢跑鞋", "跑鞋",
          "笔电", "笔记本",
          "laptop", "笔记本",
          "headphones", "耳机",
          "双肩包", "背包");

  /** 用于拆分组合词的内置词典，例如"轻量跑鞋"拆成"轻量""跑鞋"。 */
  private static final List<String> COMPOUND_WORDS =
      List.of(
          "笔记本电脑", "无线耳机", "降噪耳机", "机械键盘", "越野跑鞋", "跑鞋", "背包", "耳机", "电脑", "笔记本", "轻量", "缓震", "透气",
          "防滑", "耐磨", "网布", "纯棉", "轻薄", "通勤", "跑步", "办公", "降噪");

  /** 筛选面板上展示的属性项，顺序即展示顺序。 */
  private static final List<String> OPTION_ATTRIBUTE_KEYS =
      List.of("color", "size", "material", "useCase");

  private static final String PRODUCT_ATTRIBUTE_TABLE = "product_attribute";
  private static final String SKU_ATTRIBUTE_TABLE = "sku_attribute";

  /** 商品搜索 SQL：统计和分页查询匹配商品，读取品牌、属性选项和商品/SKU 属性。 */
  private final ProductSearchMapper mapper;

  /** 按结构化条件搜索商品，返回一页结果和给 Agent 的下一步建议。 */
  public Page search(ProductSearchRequest request) {
    List<String> terms = keywords(request.getQuery());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("request", request);
    params.put("patterns", terms.stream().map(term -> "%" + escapeLike(term) + "%").toList());
    params.put("take", request.getLimit());
    params.put("skip", (request.getPage() - 1) * request.getLimit());

    long total = RowValues.required(mapper.countMatches(params));
    List<Map<String, Object>> rows = mapper.selectMatchingProducts(params);

    // 拿到当前页商品后一次性批量查属性，避免每个商品单独查一次。
    List<Long> productIds = rows.stream().map(row -> RowValues.longValue(row, "id")).toList();
    Map<Long, Map<String, String>> attributesByProduct =
        attributes(PRODUCT_ATTRIBUTE_TABLE, "product_id", productIds);
    List<Hit> hits = rows.stream().map(row -> toHit(row, attributesByProduct)).toList();

    boolean hasMore = (long) request.getPage() * request.getLimit() < total;
    return new Page(
        hits,
        total,
        request.getPage(),
        request.getLimit(),
        hasMore,
        terms,
        request,
        suggestions(total, hits));
  }

  private static Hit toHit(Map<String, Object> row, Map<Long, Map<String, String>> attributes) {
    long productId = RowValues.longValue(row, "id");
    return new Hit(
        productId,
        RowValues.string(row, "name"),
        RowValues.string(row, "category"),
        RowValues.string(row, "brand"),
        RowValues.string(row, "summary"),
        RowValues.string(row, "image_key"),
        RowValues.decimal(row, "price"),
        RowValues.decimal(row, "original_price"),
        RowValues.decimal(row, "rating"),
        RowValues.intValue(row, "sales_count"),
        RowValues.intValue(row, "stock"),
        RowValues.longValue(row, "sku_id"),
        RowValues.string(row, "spec"),
        RowValues.intValue(row, "sku_count"),
        RowValues.intValue(row, "score"),
        attributes.getOrDefault(productId, Map.of()));
  }

  /** 没有结果时提示 Agent 如何放宽条件；放宽预算或库存条件前必须先问用户。 */
  private static List<String> suggestions(long total, List<Hit> hits) {
    if (total == 0) {
      return List.of("当前条件没有匹配商品；可减少关键词或调整品牌、颜色、尺码", "调整预算或缺货筛选前，请先征求用户同意");
    }
    if (hits.isEmpty()) {
      return List.of("该页已超出结果范围，请返回第一页");
    }
    return List.of();
  }

  /** 给商品详情补上商品级属性和每个 SKU 的规格属性（直接修改并返回传入的 profile）。 */
  public ProductProfile enrich(ProductProfile profile) {
    profile.attributes =
        attributes(PRODUCT_ATTRIBUTE_TABLE, "product_id", List.of(profile.id))
            .getOrDefault(profile.id, Map.of());
    List<Long> skuIds = profile.skus.stream().map(sku -> sku.id).toList();
    Map<Long, Map<String, String>> attributesBySku =
        attributes(SKU_ATTRIBUTE_TABLE, "sku_id", skuIds);
    profile.skus.forEach(sku -> sku.attributes = attributesBySku.getOrDefault(sku.id, Map.of()));
    return profile;
  }

  /**
   * 读取筛选面板的可选项（品牌和各属性值）。
   *
   * <p>只按品类限定，选项之间不互相过滤，否则用户选了一个颜色后，其他颜色就从面板上消失了。
   */
  public Map<String, List<String>> options(String category) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("category", category == null || category.isBlank() ? null : category);
    Map<String, List<String>> options = new LinkedHashMap<>();
    options.put("brand", mapper.selectBrands(args));
    List<Map<String, Object>> attributeRows = mapper.selectAttributeOptions(args);
    for (String key : OPTION_ATTRIBUTE_KEYS) {
      List<String> values =
          attributeRows.stream()
              .filter(row -> key.equals(row.get("attr_key")))
              .map(row -> row.get("attr_value").toString())
              .sorted()
              .distinct()
              .toList();
      options.put(key, values);
    }
    return options;
  }

  /**
   * 按编号批量查询商品属性或 SKU 属性，返回"编号 → 属性键值"。
   *
   * <p>table/idColumn 只接受两组固定组合，防止调用方误传其他表名。
   */
  private Map<Long, Map<String, String>> attributes(String table, String idColumn, List<Long> ids) {
    Map<Long, Map<String, String>> attributesById = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return attributesById;
    }
    boolean productAttributes =
        PRODUCT_ATTRIBUTE_TABLE.equals(table) && "product_id".equals(idColumn);
    boolean skuAttributes = SKU_ATTRIBUTE_TABLE.equals(table) && "sku_id".equals(idColumn);
    if (!productAttributes && !skuAttributes) {
      throw new IllegalArgumentException("不允许的属性表");
    }
    List<Map<String, Object>> rows =
        mapper.selectAttributes(parameters("ids", ids, "sku", skuAttributes));
    for (Map<String, Object> row : rows) {
      attributesById
          .computeIfAbsent(RowValues.longValue(row, idColumn), id -> new LinkedHashMap<>())
          .put(RowValues.string(row, "attr_key"), RowValues.string(row, "attr_value"));
    }
    return attributesById;
  }

  /** 转义 LIKE 通配符（SQL 里用 ESCAPE '!'），让用户输入的 % 和 _ 按字面匹配。 */
  private static String escapeLike(String term) {
    return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  /**
   * 把搜索词拆成关键词：转小写 → 按分隔符切分 → 同义词归一 → 拆组合词 → 去重。
   *
   * @throws IllegalArgumentException 关键词超过 8 个
   */
  static List<String> keywords(String query) {
    if (query == null) {
      return List.of();
    }
    List<String> terms =
        Arrays.stream(query.toLowerCase(Locale.ROOT).split(KEYWORD_SEPARATORS))
            .filter(term -> !term.isBlank())
            .map(term -> SYNONYMS.getOrDefault(term, term))
            .flatMap(term -> splitCompound(term).stream())
            .distinct()
            .toList();
    if (terms.size() > MAX_KEYWORDS) {
      throw new IllegalArgumentException("关键词最多 8 个，请精简搜索条件");
    }
    return terms;
  }

  /** 用内置词典把组合词拆开，每一步取能匹配上的最长词。整个词必须能被完整拆开，否则原样保留， 例如"轻量跑鞋"→["轻量","跑鞋"]，"轻量款"→["轻量款"]。 */
  private static List<String> splitCompound(String term) {
    String remaining = term;
    List<String> parts = new ArrayList<>();
    while (!remaining.isEmpty()) {
      String prefixToMatch = remaining;
      String longestWord =
          COMPOUND_WORDS.stream()
              .filter(prefixToMatch::startsWith)
              .max(Comparator.comparingInt(String::length))
              .orElse(null);
      if (longestWord == null) {
        return List.of(term);
      }
      parts.add(longestWord);
      remaining = remaining.substring(longestWord.length());
    }
    return parts;
  }

  /** 按键值对组装 Mapper 参数；值保持原类型，允许为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
