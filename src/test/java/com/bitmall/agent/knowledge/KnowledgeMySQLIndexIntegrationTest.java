package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexRepository;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** 隔离 MySQL 的 V19/行锁/原子发布/JSON 回读；不调用真实模型。 */
@SpringBootTest(properties = "app.knowledge.vector-store=LEGACY")
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
class KnowledgeMySQLIndexIntegrationTest {
  @Autowired KnowledgeRepository documents;
  @Autowired KnowledgeIndexRepository indexes;
  @Autowired JdbcClient jdbc;

  @Test
  void publishesAndRetainsPreviousVersionOnFailedEmbedding() {
    var properties = new KnowledgeProperties();
    properties.setEmbeddingModel("mysql-fixture");
    properties.setEmbeddingVersion("v1");
    properties.setEmbeddingDimension(2);
    var service =
        new KnowledgeIndexService(
            documents,
            indexes,
            (texts, deadline) -> texts.stream().map(q -> new double[] {1, 0.25}).toList(),
            properties);
    String version = service.rebuild();
    var snapshot = service.snapshot();
    assertThat(snapshot.getIndexVersion()).isEqualTo(version);
    assertThat(snapshot.getChunks()).hasSize(22);
    assertThat(indexes.search(version, "苹果退货", "APPLE", "RETURNS", 10)).hasSize(1);
    var failing =
        new KnowledgeIndexService(
            documents,
            indexes,
            (texts, deadline) -> {
              throw new IllegalStateException("fixture outage");
            },
            properties);
    assertThatIllegalStateException().isThrownBy(failing::rebuild);
    assertThat(service.snapshot().getIndexVersion()).isEqualTo(version);
  }
}
