package com.bitmall.agent.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.service.OrderLifecycleService;
import com.bitmall.agent.order.service.OrderSubmissionService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/** 验证完整 V17 数据上的真实商品与交易，不加载旧演示目录测试夹具。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:real_catalog_live;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.catalog-cache.enabled=false"
    })
@ActiveProfiles("demo")
class RealCatalogIntegrationTest {
  @Autowired ProductService products;
  @Autowired CheckoutService checkout;
  @Autowired OrderSubmissionService submissions;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;
  @Autowired com.bitmall.agent.agent.service.tool.ToolRegistry tools;

  @Test
  void everyEnabledProductMatchesVerifiedManifestAndLocalPhoto() throws Exception {
    var manifest =
        json.readTree(new ClassPathResource("catalog/real-products.json").getInputStream());
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM product WHERE enabled=TRUE")
                .query(Integer.class)
                .single())
        .isEqualTo(32);
    for (var expected : manifest.path("products")) {
      var actual = products.product(expected.path("id").asLong());
      assertThat(actual.getName()).isEqualTo(expected.path("name").asText());
      assertThat(actual.getAttributes())
          .containsEntry("sourceUrl", expected.path("sourceUrl").asText());
      assertThat(actual.getRating()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(actual.getSalesCount()).isZero();
      assertThat(new ClassPathResource("static" + expected.path("imagePath").asText()).exists())
          .isTrue();
      for (var sku : actual.getSkus()) {
        var expectedSku =
            java.util.stream.StreamSupport.stream(expected.path("skus").spliterator(), false)
                .filter(s -> s.path("id").asLong() == sku.getId())
                .findFirst()
                .orElseThrow();
        assertThat(sku.getPrice()).isEqualByComparingTo(expectedSku.path("price").decimalValue());
        assertThat(sku.getSpec()).isEqualTo(expectedSku.path("spec").asText());
      }
    }
  }

  @Test
  void assistantReadsSameRealProductAndSource() {
    var result =
        (com.bitmall.agent.product.vo.ProductDetailVO)
            tools
                .require("product_detail")
                .getHandler()
                .execute(
                    new com.bitmall.agent.agent.service.tool.ToolExecutionContext(
                        10001, "real-catalog", "source-check"),
                    java.util.Map.of("productId", 205));
    assertThat(result.getName()).isEqualTo("Apple iPhone 17");
    assertThat(result.getAttributes()).containsEntry("priceBasis", "官网标价快照");
    assertThat(result.getSkus()).allMatch(s -> s.getId() >= 20500);
  }

  @Test
  void retiredProductCannotBeUsedForNewPurchase() {
    assertThatThrownBy(() -> products.product(101)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> checkout.checkoutQuote(10001, 1001, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void realSkuPriceFlowsThroughQuoteIdempotentOrderAndCancellation() {
    int before =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=20101").query(Integer.class).single();
    var quote = checkout.checkoutQuote(10001, 20101, 1, null);
    assertThat(quote.getPayAmount()).isEqualByComparingTo("12999");
    var purchase = new CreateOrderCommand("DIRECT", 20101L, 1, null, null, quote.getPayAmount());
    String trace = UUID.randomUUID().toString();
    var first = submissions.submit(10001, trace, purchase);
    var second = submissions.submit(10001, trace, purchase);
    assertThat(first.getStatus()).isEqualTo("SUCCESS");
    assertThat(second.getOrderId()).isEqualTo(first.getOrderId());
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=20101").query(Integer.class).single())
        .isEqualTo(before - 1);
    assertThat(
            jdbc.sql("SELECT product_name FROM order_item WHERE order_id=:id")
                .param("id", first.getOrderId())
                .query(String.class)
                .single())
        .contains("MacBook Pro");
    lifecycle.cancel(10001, first.getOrderId());
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=20101").query(Integer.class).single())
        .isEqualTo(before);
  }
}
