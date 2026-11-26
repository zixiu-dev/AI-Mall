package com.bitmall.agent.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bitmall.agent.support.LegacyMigrationFixtures;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 从 V16 升级到真实目录，独立验证不复用旧 SKU、不篡改历史快照和库存。 */
@EnabledIf("com.bitmall.agent.support.LegacyMigrationFixtures#hasH2History")
class RealCatalogMigrationTest {
  @Test
  void upgradesExistingCatalogWithoutRewritingHistoricalFacts() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:catalog_upgrade_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure().dataSource(source).locations(LegacyMigrationFixtures.h2Location()).target("16").load().migrate();
    var jdbc = new JdbcTemplate(source);
    var oldItems = jdbc.queryForList("SELECT * FROM order_item ORDER BY id");
    var oldOrders = jdbc.queryForList("SELECT * FROM customer_order ORDER BY id");
    var oldStock = jdbc.queryForList("SELECT id,stock,locked_stock,redis_inventory_initialized FROM product_sku ORDER BY id");
    // 模拟旧库主动清理演示偏好，升级后不能被新库的种子脚本恢复。
    jdbc.update("DELETE FROM user_memory");
    Flyway.configure().dataSource(source).locations("classpath:db/migration", LegacyMigrationFixtures.h2Location()).load().migrate();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE enabled=TRUE",Integer.class)).isEqualTo(32);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id BETWEEN 101 AND 132 AND enabled=TRUE",Integer.class)).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM order_item ORDER BY id")).isEqualTo(oldItems);
    assertThat(jdbc.queryForList("SELECT * FROM customer_order ORDER BY id")).isEqualTo(oldOrders);
    assertThat(jdbc.queryForList("SELECT id,stock,locked_stock,redis_inventory_initialized FROM product_sku WHERE id<20000 ORDER BY id")).isEqualTo(oldStock);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_sku WHERE id>=20000",Integer.class)).isEqualTo(38);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_attribute WHERE attr_key='sourceUrl' AND product_id>=201",Integer.class)).isEqualTo(32);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_memory", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
          AND COALESCE(remarks, '') = ''
        """, Integer.class)).isZero();
    assertThat(jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
          AND COALESCE(remarks, '') = ''
        """, Integer.class)).isZero();
  }
}
