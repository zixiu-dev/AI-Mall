package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 真正连接失败时，普通业务/KEYWORD 不依赖 PG，HYBRID 明确降级且有界返回。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:pgvector_unavailable;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.knowledge.vector-store=PGVECTOR",
      "app.knowledge.pgvector.url=jdbc:postgresql://127.0.0.1:1/unavailable",
      "app.catalog-cache.enabled=false",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
class PgVectorUnavailableIntegrationTest {
  @Autowired KnowledgeService knowledge;
  @Autowired KnowledgeProperties properties;
  @Autowired ProductService products;

  @Test
  void unavailablePgDoesNotBreakCommerceAndReportsHybridDegradation() {
    assertThat(products.product(201L)).isNotNull();
    properties.setMode("KEYWORD");
    assertThat(knowledge.retrieve("Apple退货", null, null, null).getStatus()).isEqualTo("FOUND");
    properties.setMode("HYBRID");
    long start = System.nanoTime();
    var result = knowledge.retrieve("Apple退货", null, null, null);
    assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD");
    assertThat(result.getWarnings()).anyMatch(w -> w.contains("VECTOR_INDEX_UNAVAILABLE"));
    assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
        .isLessThan(9000);
  }
}
