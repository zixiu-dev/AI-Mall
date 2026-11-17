package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V18 只升级知识资料，不能顺便重写订单、库存、商品或旧文档正文。 */
class BrandPolicyMigrationTest {
  @Test
  void upgradesKnowledgeWithoutRewritingTransactionFacts() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:policy_upgrade_"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .target("17")
        .load()
        .migrate();
    var jdbc = new JdbcTemplate(ds);
    var orders = jdbc.queryForList("SELECT * FROM customer_order ORDER BY id");
    var skus = jdbc.queryForList("SELECT * FROM product_sku ORDER BY id");
    var original = jdbc.queryForList("SELECT id,title,content FROM knowledge_document ORDER BY id");
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    assertThat(jdbc.queryForList("SELECT * FROM customer_order ORDER BY id")).isEqualTo(orders);
    assertThat(jdbc.queryForList("SELECT * FROM product_sku ORDER BY id")).isEqualTo(skus);
    assertThat(
            jdbc.queryForList(
                "SELECT id,title,content FROM knowledge_document WHERE id<10000 ORDER BY id"))
        .isEqualTo(original);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE enabled=TRUE AND"
                    + " brand_code<>'PLATFORM'",
                Integer.class))
        .isEqualTo(16);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE enabled=TRUE AND"
                    + " brand_code='PLATFORM'",
                Integer.class))
        .isEqualTo(6);
  }
}
