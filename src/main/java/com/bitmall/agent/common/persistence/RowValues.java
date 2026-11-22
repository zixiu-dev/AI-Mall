package com.bitmall.agent.common.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 读取 MyBatis 自定义投影返回的 Map 行，并转换成 Java 类型。
 *
 * <p>只做类型转换，不执行 SQL。列名先按原样查找，找不到再按大写查找，因为 H2 默认把未加引号的列名转成大写，而 MySQL 保持原样。
 */
public final class RowValues {
  private RowValues() {}

  /** 取出列的原始值；同时兼容 MySQL 的小写列名和 H2 的大写列名。 */
  public static Object value(Map<String, Object> row, String column) {
    if (row.containsKey(column)) {
      return row.get(column);
    }
    return row.get(column.toUpperCase(Locale.ROOT));
  }

  /** 按字符串读取；数据库值为 null 时返回 null。 */
  public static String string(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw == null) {
      return null;
    }
    return raw.toString();
  }

  /** 按 long 读取；数据库值为 null 时返回 0。 */
  public static long longValue(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw == null) {
      return 0;
    }
    return ((Number) raw).longValue();
  }

  /** 按 int 读取；数据库值为 null 时返回 0。 */
  public static int intValue(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw == null) {
      return 0;
    }
    return ((Number) raw).intValue();
  }

  /** 按布尔读取；兼容驱动返回 Boolean、数字（非 0 为真）或字符串 "true"，null 视为 false。 */
  public static boolean bool(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw instanceof Boolean flag) {
      return flag;
    }
    if (raw instanceof Number number) {
      return number.intValue() != 0;
    }
    if (raw == null) {
      return false;
    }
    return Boolean.parseBoolean(raw.toString());
  }

  /** 按金额等精确小数读取，经字符串转换以避免 double 精度误差；null 时返回 null。 */
  public static BigDecimal decimal(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw == null) {
      return null;
    }
    return new BigDecimal(raw.toString());
  }

  /** 按时间戳读取；不同驱动可能返回 Timestamp 或 LocalDateTime，统一转成 Timestamp。 */
  public static Timestamp timestamp(Map<String, Object> row, String column) {
    Object raw = value(row, column);
    if (raw == null) {
      return null;
    }
    if (raw instanceof Timestamp timestamp) {
      return timestamp;
    }
    return Timestamp.valueOf((LocalDateTime) raw);
  }

  /** 取出必须恰好一行的查询结果（如 count），行数不对说明 SQL 或数据有误，直接报错。 */
  public static <T> T required(List<T> rows) {
    if (rows.size() != 1) {
      throw new IllegalStateException("期望一行查询结果，实际为 " + rows.size());
    }
    return rows.get(0);
  }

  /** 取出最多一行的查询结果；没有结果或该行值为 null 时返回空，多于一行时报错。 */
  public static <T> Optional<T> optional(List<T> rows) {
    if (rows.size() > 1) {
      throw new IllegalStateException("单行查询返回多条记录");
    }
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(rows.get(0));
  }
}
