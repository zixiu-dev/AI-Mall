package com.bitmall.agent.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/** query 保存完整诉求，queries 只表达外层已选出的知识子问题；不接受模型提供身份或预算。 */
@Getter
public final class KnowledgeRetrievalRequest {
  private final String query;
  private final String brand;
  private final String category;
  private final String scope;
  private final List<String> queries;

  @JsonCreator
  public KnowledgeRetrievalRequest(
      @JsonProperty("query") String query,
      @JsonProperty("queries") List<String> queries,
      @JsonProperty("brand") String brand,
      @JsonProperty("category") String category,
      @JsonProperty("scope") String scope) {
    if (query == null || query.isBlank() || query.length() > 1000) {
      throw new IllegalArgumentException("query 应为 1～1000 字符");
    }
    this.query = query;
    this.brand = brand;
    this.category = category;
    this.scope = scope;
    if (queries != null && (brand != null || category != null || scope != null)) {
      throw new IllegalArgumentException("queries 不能与 brand/category/scope 同时提供");
    }
    if (queries != null && (queries.isEmpty() || queries.size() > 20)) {
      throw new IllegalArgumentException("queries 应为 1～3 条有效子问题");
    }
    var unique = new LinkedHashMap<String, String>();
    if (queries != null) {
      for (Object item : queries) {
        if (!(item instanceof String s) || s.isBlank() || s.length() > 500) {
          throw new IllegalArgumentException("queries 元素必须为 1～500 字符的非空字符串");
        }
        unique.putIfAbsent(s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT), s.trim());
      }
    }
    if (unique.size() > 3) {
      throw new IllegalArgumentException("最多 3 条不同知识子问题");
    }
    this.queries = queries == null ? null : List.copyOf(unique.values());
  }

  /** 工具的 Map 参数仍做执行层类型检查，不依赖 Schema 声明。 */
  public static KnowledgeRetrievalRequest from(Map<String, Object> args) {
    if (args.keySet().stream()
        .anyMatch(k -> !List.of("query", "queries", "brand", "category", "scope").contains(k))) {
      throw new IllegalArgumentException("知识检索包含未知参数");
    }
    Object q = args.get("query"), qs = args.get("queries");
    if (!(q instanceof String)) {
      throw new IllegalArgumentException("query 必须为字符串");
    }
    if (qs != null && !(qs instanceof List<?>)) {
      throw new IllegalArgumentException("queries 必须为字符串数组");
    }
    List<String> values = null;
    if (qs instanceof List<?> list) {
      values = new ArrayList<>();
      for (Object v : list) {
        if (!(v instanceof String s)) {
          throw new IllegalArgumentException("queries 元素必须为字符串");
        }
        values.add(s);
      }
    }
    return new KnowledgeRetrievalRequest(
        (String) q, values, text(args, "brand"), text(args, "category"), text(args, "scope"));
  }

  private static String text(Map<String, Object> a, String k) {
    Object v = a.get(k);
    if (v != null && !(v instanceof String)) {
      throw new IllegalArgumentException(k + " 必须为字符串");
    }
    return (String) v;
  }
}
