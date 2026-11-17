package com.bitmall.agent.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 从 V16 升级到真实目录，独立验证不复用旧 SKU、不篡改历史快照和库存。 */
class RealCatalogMigrationTest {
  @Test
  void upgradesExistingCatalogWithoutRewritingHistoricalFacts() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:catalog_upgrade_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure().dataSource(source).locations("classpath:db/migration").target("16").load().migrate();
    var jdbc = new JdbcTemplate(source);
    var oldItems = jdbc.queryForList("SELECT * FROM order_item ORDER BY id");
    var oldOrders = jdbc.queryForList("SELECT * FROM customer_order ORDER BY id");
    var oldStock = jdbc.queryForList("SELECT id,stock,locked_stock,redis_inventory_initialized FROM product_sku ORDER BY id");
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE enabled=TRUE",Integer.class)).isEqualTo(32);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id BETWEEN 101 AND 132 AND enabled=TRUE",Integer.class)).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM order_item ORDER BY id")).isEqualTo(oldItems);
    assertThat(jdbc.queryForList("SELECT * FROM customer_order ORDER BY id")).isEqualTo(oldOrders);
    assertThat(jdbc.queryForList("SELECT id,stock,locked_stock,redis_inventory_initialized FROM product_sku WHERE id<20000 ORDER BY id")).isEqualTo(oldStock);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_sku WHERE id>=20000",Integer.class)).isEqualTo(38);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_attribute WHERE attr_key='sourceUrl' AND product_id>=201",Integer.class)).isEqualTo(32);
  }
}
