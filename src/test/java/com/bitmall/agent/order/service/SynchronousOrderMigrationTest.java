package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.order.config.SyncOrderMigrationGuard;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** 使用真实旧版本数据库验证升级边界，不能为了删活动表遗失已经受理的购买。 */
class SynchronousOrderMigrationTest {
  @Test
  void unfinishedLegacyAsyncPurchaseBlocksRemovalUntilReconciled() throws Exception {
    String url =
        "jdbc:h2:mem:upgrade-"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target("12")
        .load()
        .migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO"
              + " order_submit_trace(trace_id,user_id,mode,activity_id,request_snapshot,request_hash,expires_at)"
              + " VALUES ('legacy-trace',10001,'ASYNC','demo-hot-sale','{}','"
              + "0".repeat(64)
              + "',CURRENT_TIMESTAMP)");
      var upgrade =
          Flyway.configure()
              .dataSource(url, "sa", "")
              .locations("classpath:db/migration")
              .callbacks(new SyncOrderMigrationGuard())
              .load();
      assertThatThrownBy(upgrade::migrate).hasStackTraceContaining("仍有旧异步购买");
      // 拒绝发生在任何删除或新字段变更之前，旧版本仍能完成恢复。
      try (var rows = statement.executeQuery("SELECT COUNT(*) FROM order_sale_activity")) {
        rows.next();
        assertThat(rows.getInt(1)).isPositive();
      }
      statement.executeUpdate(
          "UPDATE order_submit_trace SET status='FAILED',reserve_status='RELEASED' WHERE"
              + " trace_id='legacy-trace'");
      upgrade.migrate();
      try (var rows =
          statement.executeQuery(
              "SELECT status FROM order_submit_trace WHERE trace_id='legacy-trace'")) {
        rows.next();
        assertThat(rows.getString(1)).isEqualTo("FAILED");
      }
      assertThatThrownBy(() -> statement.executeQuery("SELECT COUNT(*) FROM order_sale_activity"))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }
}
