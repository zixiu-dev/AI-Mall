package com.bitmall.agent.support;

import java.nio.file.Files;
import java.nio.file.Path;

/** 旧库升级验证按需读取源码外的 Flyway 历史。 */
public final class LegacyMigrationFixtures {
  private LegacyMigrationFixtures() {}

  /** 判断本地是否具备可供旧版 H2 回放的迁移文件。 */
  public static boolean hasH2History() {
    return Files.isRegularFile(h2Directory().resolve("V1__schema.sql"))
        && Files.isRegularFile(h2Directory().resolve("V22__describe_business_schema.sql"));
  }

  /** 供 Flyway 使用的 H2 历史位置，与应用使用同一个环境变量。 */
  public static String h2Location() {
    return "filesystem:" + h2Directory();
  }

  private static Path h2Directory() {
    String root = System.getenv().getOrDefault("DB_HISTORY_DIR", ".runtime/db-history");
    return Path.of(root).resolve("migration").toAbsolutePath();
  }
}
