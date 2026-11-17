package com.bitmall.agent.common.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/** 自定义联表投影的类型转换；只读取 MyBatis 返回值，不执行 SQL。 */
public final class RowValues {
  private RowValues() {}

  public static Object value(Map<String, Object> row, String column) {
    if (row.containsKey(column)) return row.get(column);
    return row.get(column.toUpperCase(java.util.Locale.ROOT));
  }

  public static String string(Map<String, Object> row, String column) {
    Object v = value(row, column);
    return v == null ? null : v.toString();
  }

  public static long longValue(Map<String, Object> row, String column) {
    Object v = value(row, column);
    return v == null ? 0 : ((Number) v).longValue();
  }

  public static int intValue(Map<String, Object> row, String column) {
    Object v = value(row, column);
    return v == null ? 0 : ((Number) v).intValue();
  }

  public static boolean bool(Map<String, Object> row, String column) {
    Object v = value(row, column);
    if (v instanceof Boolean b) return b;
    if (v instanceof Number n) return n.intValue() != 0;
    return v != null && Boolean.parseBoolean(v.toString());
  }

  public static BigDecimal decimal(Map<String, Object> row, String column) {
    Object v = value(row, column);
    return v == null ? null : new BigDecimal(v.toString());
  }

  public static Timestamp timestamp(Map<String, Object> row, String column) {
    Object v = value(row, column);
    if (v == null) return null;
    return v instanceof Timestamp t ? t : Timestamp.valueOf((LocalDateTime) v);
  }

  public static <T> T required(java.util.List<T> rows) {
    if (rows.size() != 1) throw new IllegalStateException("期望一行查询结果，实际为 " + rows.size());
    return rows.get(0);
  }

  public static <T> java.util.Optional<T> optional(java.util.List<T> rows) {
    if (rows.size() > 1) throw new IllegalStateException("单行查询返回多条记录");
    return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(rows.get(0));
  }
}
