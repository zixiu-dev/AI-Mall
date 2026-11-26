package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.config.PgVectorProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.dao.repository.PgVectorIndexRepository;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.knowledge.service.retrieval.EmbeddingClient;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeText;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 真实独立 PostgreSQL/pgvector，源库为隔离 H2；embedding 是明确标注的 1024 维可控替身。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:pgvector_source;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.knowledge.vector-store=PGVECTOR",
      "app.catalog-cache.enabled=false",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
@EnabledIfEnvironmentVariable(named = "PGVECTOR_TEST_URL", matches = ".+")
class PgVectorIndexIntegrationTest {
  @Autowired KnowledgeProperties properties;
  @Autowired PgVectorProperties pgProperties;
  @Autowired KnowledgeIndexService indexing;
  @Autowired KnowledgeService knowledge;
  @Autowired KnowledgeRepository documents;
  @Autowired JdbcClient primary;
  @Autowired DataSource primaryDataSource;
  @MockitoSpyBean PgVectorIndexRepository pg;
  @MockitoBean EmbeddingClient embedding;

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry r) {
    r.add("app.knowledge.pgvector.url", () -> System.getenv("PGVECTOR_TEST_URL"));
    r.add("app.knowledge.pgvector.username", () -> System.getenv("PGVECTOR_TEST_USERNAME"));
    r.add("app.knowledge.pgvector.password", () -> System.getenv("PGVECTOR_TEST_PASSWORD"));
  }

  @BeforeEach
  void setup() {
    properties.setMode("HYBRID");
    properties.setEmbeddingModel("pgvector-fixture");
    properties.setEmbeddingVersion("fixture-1024-v1");
    properties.setEmbeddingDimension(1024);
    pgProperties.setSearchMode("EXACT");
    when(embedding.embed(anyList(), anyLong()))
        .thenAnswer(c -> ((List<?>) c.getArgument(0)).stream().map(q -> vector(1, 0)).toList());
  }

  private static double[] vector(double first, double second) {
    var v = new double[1024];
    v[0] = first;
    v[1] = second;
    return v;
  }

  private java.sql.Connection connection() throws Exception {
    var values = new java.util.Properties();
    values.setProperty("user", pgProperties.getUsername());
    values.setProperty("password", pgProperties.getPassword());
    values.setProperty("currentSchema", "knowledge,public");
    return DriverManager.getConnection(pgProperties.getUrl(), values);
  }

  private String scalar(String sql) throws Exception {
    try (var c = connection();
        var s = c.createStatement();
        var rs = s.executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getString(1);
    }
  }

  @Test
  void nativeVectorColumnAndPrimaryDatasourceStaySeparate() throws Exception {
    String version = indexing.rebuild();
    assertThat(
            scalar(
                "SELECT format_type(atttypid,atttypmod) FROM pg_attribute WHERE"
                    + " attrelid='knowledge_index_chunk'::regclass AND attname='embedding'"))
        .isEqualTo("vector(1024)");
    assertThat(
            scalar(
                "SELECT COUNT(*) FROM knowledge_index_chunk WHERE index_version='" + version + "'"))
        .isEqualTo("22");
    assertThat(scalar("SELECT extversion FROM pg_extension WHERE extname='vector'")).isNotBlank();
    assertThat(scalar("SELECT obj_description('knowledge_index_chunk'::regclass, 'pg_class')"))
        .contains("知识切片");
    assertThat(scalar("""
        SELECT col_description(attrelid, attnum) FROM pg_attribute
        WHERE attrelid = 'knowledge_index_chunk'::regclass AND attname = 'embedding'
        """)).contains("1024 维");
    try (var c = primaryDataSource.getConnection()) {
      assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("H2");
    }
    assertThat(
            primary.sql("SELECT COUNT(*) FROM knowledge_index_chunk").query(Integer.class).single())
        .isZero();
    var snapshot = indexing.snapshot();
    assertThat(snapshot.isDatabaseVectorSearch()).isTrue();
    assertThat(snapshot.getChunks()).allMatch(c -> c.getVector().length == 0);
  }

  @Test
  void hybridUsesSamePgVersionAndKeepsHardScopes() {
    String version = indexing.rebuild();
    var result = knowledge.retrieve("Apple退货、Nike配送", null, null, null);
    assertThat(result.getStatus()).isEqualTo("FOUND");
    assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD+VECTOR");
    assertThat(result.getHits())
        .extracting(h -> h.getBrand() + "/" + h.getDocumentType())
        .containsExactly("APPLE/RETURNS", "NIKE/SHIPPING");
    assertThat(result.getHits())
        .allMatch(
            h ->
                h.getIndexVersion().equals(version) && h.getRetrievalChannels().contains("VECTOR"));
  }

  @Test
  void failedEmbeddingAndWrongDimensionCannotChangePublication() throws Exception {
    String version = indexing.rebuild();
    when(embedding.embed(anyList(), anyLong()))
        .thenThrow(new IllegalStateException("fixture outage"));
    assertThatIllegalStateException().isThrownBy(indexing::rebuild);
    assertThat(indexing.snapshot().getIndexVersion()).isEqualTo(version);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pg.begin("invalid-" + UUID.randomUUID(), "another", "v1", 2, 1));
    assertThat(indexing.snapshot().getIndexVersion()).isEqualTo(version);
  }

  @Test
  void failedPgTransactionRollsBackChunksAndPointer() {
    String version = indexing.rebuild();
    String candidate = "fixture-" + UUID.randomUUID();
    pg.begin(candidate, "pgvector-fixture", "fixture-1024-v1", 1024, 1);
    var chunk = indexing.snapshot().getChunks().get(0);
    var ready =
        new KnowledgeChunk(
            chunk.getChunkId(),
            chunk.getDocumentHash(),
            chunk.getTextHash(),
            chunk.getSectionTitle(),
            chunk.getDocument(),
            vector(1, 0));
    assertThatException().isThrownBy(() -> pg.publish(candidate, List.of(ready, ready), () -> {}));
    assertThat(indexing.snapshot().getIndexVersion()).isEqualTo(version);
    pg.fail(candidate);
  }

  @Test
  void sourceChangeAfterPublicationInvalidatesPgVectors() {
    indexing.rebuild();
    primary
        .sql("UPDATE knowledge_document SET enabled=FALSE WHERE document_key='apple-returns'")
        .update();
    try {
      var result = knowledge.retrieve("Apple退货", null, null, null);
      assertThat(result.getHits()).isEmpty();
      assertThat(result.getWarnings()).anyMatch(w -> w.contains("VECTOR_INDEX_UNAVAILABLE"));
    } finally {
      primary
          .sql("UPDATE knowledge_document SET enabled=TRUE WHERE document_key='apple-returns'")
          .update();
    }
  }

  @Test
  void vectorFailureDegradesButTwoPgBranchesFailAsError() {
    indexing.rebuild();
    doThrow(new IllegalStateException("fixture pg vector failure"))
        .when(pg)
        .vectorSearch(anyString(), any(), anyString(), anyString(), anyInt());
    assertThat(knowledge.retrieve("Apple退货", null, null, null).getRetrievalMode())
        .isEqualTo("DB_KEYWORD");
    doThrow(new IllegalStateException("fixture pg keyword failure"))
        .when(pg)
        .search(anyString(), anyString(), anyString(), anyString(), anyInt());
    assertThatIllegalStateException()
        .isThrownBy(() -> knowledge.retrieve("Apple退货", null, null, null))
        .withMessageContaining("基础设施");
  }

  @Test
  void keywordModeDoesNotTouchPg() {
    properties.setMode("KEYWORD");
    clearInvocations(pg);
    assertThat(knowledge.retrieve("Apple退货", null, null, null).getHits()).isNotEmpty();
    verifyNoInteractions(pg);
  }

  @Test
  void databaseCosineOrdersKnownVectorsWithinLegalScope() {
    var doc =
        documents.listEnabled().stream()
            .filter(d -> d.getDocumentKey().equals("apple-returns"))
            .findFirst()
            .orElseThrow();
    String version = "distance-" + UUID.randomUUID();
    pg.begin(version, "pgvector-fixture", "fixture-1024-v1", 1024, 1);
    var chunks = new ArrayList<KnowledgeChunk>();
    String[] ids = {"near", "orthogonal", "opposite"};
    double[][] values = {vector(1, 0), vector(0, 1), vector(-1, 0)};
    for (int i = 0; i < 3; i++)
      chunks.add(
          new KnowledgeChunk(
              ids[i],
              KnowledgeText.documentHash(doc),
              KnowledgeText.hash(doc.getContent()),
              doc.getTitle(),
              doc,
              values[i]));
    pg.publish(version, chunks, () -> {});
    var result = pg.vectorSearch(version, vector(1, 0), "APPLE", "RETURNS", 3);
    assertThat(result)
        .extracting(row -> row.get("chunk_id"))
        .containsExactly("near", "orthogonal", "opposite");
    for (int i = 0; i < 3; i++)
      assertThat(((Number) result.get(i).get("distance")).doubleValue()).isCloseTo(i, within(1e-6));
  }

  @Test
  void hnswFiltersSparseScopeAndNativeIndexCanExecute() throws Exception {
    indexing.rebuild();
    var docs = documents.listEnabled();
    var apple =
        docs.stream()
            .filter(d -> d.getDocumentKey().equals("apple-returns"))
            .findFirst()
            .orElseThrow();
    var nike =
        docs.stream()
            .filter(d -> d.getDocumentKey().equals("nike-returns"))
            .findFirst()
            .orElseThrow();
    String version = "ann-" + UUID.randomUUID();
    pg.begin(version, "pgvector-fixture", "fixture-1024-v1", 1024, 2);
    var chunks = new ArrayList<KnowledgeChunk>();
    for (int i = 0; i < 512; i++) {
      var d = i < 16 ? apple : nike;
      chunks.add(
          new KnowledgeChunk(
              "fixture-" + i,
              KnowledgeText.documentHash(d),
              KnowledgeText.hash(d.getContent()),
              d.getTitle(),
              d,
              i < 16 ? vector(.8, .2) : vector(1, 0)));
    }
    pg.publish(version, chunks, () -> {});
    pgProperties.setSearchMode("HNSW");
    var hits = pg.vectorSearch(version, vector(1, 0), "APPLE", "RETURNS", 10);
    assertThat(hits).hasSize(10);
    assertThat(hits)
        .allMatch(row -> Integer.parseInt(row.get("chunk_id").toString().substring(8)) < 16);
    try (var c = connection();
        var s = c.createStatement()) {
      s.execute("SET enable_seqscan=off");
      var query = new tools.jackson.databind.ObjectMapper().writeValueAsString(vector(1, 0));
      try (var rs =
          s.executeQuery(
              "EXPLAIN SELECT chunk_id FROM knowledge_index_chunk ORDER BY embedding <=> '"
                  + query
                  + "'::vector LIMIT 10")) {
        var plan = new StringBuilder();
        while (rs.next()) plan.append(rs.getString(1)).append('\n');
        assertThat(plan.toString()).contains("idx_pgvector_chunk_hnsw");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(".runtime/pgvector"));
        java.nio.file.Files.writeString(
            java.nio.file.Path.of(".runtime/pgvector/hnsw-plan.txt"), plan.toString());
      }
    }
  }
}
