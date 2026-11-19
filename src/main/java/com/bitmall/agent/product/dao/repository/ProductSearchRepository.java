package com.bitmall.agent.product.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.product.cache.ProductProfile;
import com.bitmall.agent.product.dao.mapper.ProductSearchMapper;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.vo.ProductSearchResultVO.Hit;
import com.bitmall.agent.product.vo.ProductSearchResultVO.Page;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 结构化商品搜索的数据访问组件。 构造固定 SQL 片段并绑定用户条件，保证同一 SKU 满足预算、规格和库存，再排序、分页、补属性。 */
@Repository
public class ProductSearchRepository {
  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final ProductSearchMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ProductSearchRepository(ProductSearchMapper mapper) {
    this.mapper = mapper;
  }

  /** 构造受参数绑定保护的结构化搜索 SQL；所有规格条件作用于同一个 SKU，先排序再分页。 */
  public Page search(ProductSearchRequest r) {
    List<String> terms = keywords(r.getQuery());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("request", r);
    params.put("patterns", terms.stream().map(t -> "%" + escape(t) + "%").toList());
    params.put("take", r.getLimit());
    params.put("skip", (r.getPage() - 1) * r.getLimit());
    long total = RowValues.required(mapper.countMatches(params));
    List<Hit> rows =
        mapper.selectMatchingProducts(params).stream()
            .map(
                row ->
                    new Hit(
                        RowValues.longValue(row, "id"),
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
                        Map.of()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    // 5. 得到当前页商品后再批量补属性，避免为每个商品分别发一次属性查询。
    Map<Long, Map<String, String>> attrs =
        attributes("product_attribute", "product_id", rows.stream().map(Hit::getId).toList());
    List<Hit> hits =
        rows.stream()
            .map(
                h ->
                    new Hit(
                        h.getId(),
                        h.getName(),
                        h.getCategory(),
                        h.getBrand(),
                        h.getSummary(),
                        h.getImageKey(),
                        h.getPrice(),
                        h.getOriginalPrice(),
                        h.getRating(),
                        h.getSalesCount(),
                        h.getStock(),
                        h.getMatchedSkuId(),
                        h.getMatchedSpec(),
                        h.getMatchedSkuCount(),
                        h.getScore(),
                        attrs.getOrDefault(h.getId(), Map.of())))
            .toList();
    List<String> suggestions =
        total == 0
            ? List.of("当前条件没有匹配商品；可减少关键词或调整品牌、颜色、尺码", "调整预算或缺货筛选前，请先征求用户同意")
            : hits.isEmpty() ? List.of("该页已超出结果范围，请返回第一页") : List.of();
    return new Page(
        hits,
        total,
        r.getPage(),
        r.getLimit(),
        (long) r.getPage() * r.getLimit() < total,
        terms,
        r,
        suggestions);
  }

  /** 批量读取商品和规格属性，补充到详情中。 */
  public ProductProfile enrich(ProductProfile profile) {
    profile.attributes =
        attributes("product_attribute", "product_id", List.of(profile.id))
            .getOrDefault(profile.id, Map.of());
    var details =
        attributes("sku_attribute", "sku_id", profile.skus.stream().map(v -> v.id).toList());
    profile.skus.forEach(v -> v.attributes = details.getOrDefault(v.id, Map.of()));
    return profile;
  }

  /** 从当前启用目录读取筛选选项；只按品类限定，不让选项相互过滤。 */
  public Map<String, List<String>> options(String category) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("category", category == null || category.isBlank() ? null : category);
    Map<String, List<String>> out = new LinkedHashMap<>();
    out.put("brand", mapper.selectBrands(args));
    List<Map<String, Object>> values = mapper.selectAttributeOptions(args);
    for (String key : List.of("color", "size", "material", "useCase"))
      out.put(
          key,
          values.stream()
              .filter(v -> key.equals(v.get("attr_key")))
              .map(v -> v.get("attr_value").toString())
              .sorted()
              .distinct()
              .toList());
    return out;
  }

  /** 根据内部指定的表和编号列批量查询属性，避免每个商品单独查一次。 */
  private Map<Long, Map<String, String>> attributes(String table, String id, List<Long> ids) {
    Map<Long, Map<String, String>> out = new LinkedHashMap<>();
    if (ids.isEmpty()) return out;
    if (!(table.equals("product_attribute") && id.equals("product_id"))
        && !(table.equals("sku_attribute") && id.equals("sku_id")))
      throw new IllegalArgumentException("不允许的属性表");
    mapper.selectAttributes(parameters("ids", ids, "sku", table.equals("sku_attribute"))).stream()
        .map(
            row -> {
              out.computeIfAbsent(RowValues.longValue(row, id), k -> new LinkedHashMap<>())
                  .put(RowValues.string(row, "attr_key"), RowValues.string(row, "attr_value"));
              return 0;
            })
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    return out;
  }

  /** 转义 LIKE 的特殊字符，让用户输入的百分号和下划线按字面搜索。 */
  private static String escape(String s) {
    return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  /** 拆分和归一化关键词，处理有限同义词，并限制关键词数量。 */
  static List<String> keywords(String query) {
    if (query == null) return List.of();
    Map<String, String> synonyms =
        Map.of(
            "运动鞋",
            "跑鞋",
            "慢跑鞋",
            "跑鞋",
            "笔电",
            "笔记本",
            "laptop",
            "笔记本",
            "headphones",
            "耳机",
            "双肩包",
            "背包");
    List<String> terms =
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,，、;；]+"))
            .filter(s -> !s.isBlank())
            .map(s -> synonyms.getOrDefault(s, s))
            .flatMap(s -> splitCompound(s).stream())
            .distinct()
            .toList();
    if (terms.size() > 8) throw new IllegalArgumentException("关键词最多 8 个，请精简搜索条件");
    return terms;
  }

  /** 按项目内置词典拆分完全可识别的组合词；无法完整识别时保留原词。 */
  private static List<String> splitCompound(String term) {
    List<String> words =
        List.of(
            "笔记本电脑", "无线耳机", "降噪耳机", "机械键盘", "越野跑鞋", "跑鞋", "背包", "耳机", "电脑", "笔记本", "轻量", "缓震",
            "透气", "防滑", "耐磨", "网布", "纯棉", "轻薄", "通勤", "跑步", "办公", "降噪");
    String remaining = term;
    List<String> parts = new ArrayList<>();
    while (!remaining.isEmpty()) {
      String rest = remaining;
      String word =
          words.stream()
              .filter(rest::startsWith)
              .max(Comparator.comparingInt(String::length))
              .orElse(null);
      if (word == null) return List.of(term);
      parts.add(word);
      remaining = remaining.substring(word.length());
    }
    return parts;
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
