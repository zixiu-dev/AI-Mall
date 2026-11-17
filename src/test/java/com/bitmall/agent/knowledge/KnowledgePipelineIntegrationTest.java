package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexRepository;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.knowledge.service.retrieval.EmbeddingClient;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeEvidencePacker;
import com.bitmall.agent.knowledge.service.retrieval.QueryEnhancer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 真实 H2/SQL/索引发布与检索流水线；2 维 embedding 明确是可控替身，不证明语义模型效果。 */
@SpringBootTest(
    properties = {
      "app.knowledge.vector-store=LEGACY",
      "spring.datasource.url=jdbc:h2:mem:rag_pipeline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.catalog-cache.enabled=false",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class KnowledgePipelineIntegrationTest {
  @Autowired KnowledgeService knowledge;
  @Autowired KnowledgeIndexService indexing;
  @Autowired KnowledgeProperties properties;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  @MockitoBean EmbeddingClient embedding;
  @MockitoBean QueryEnhancer enhancer;
  @MockitoSpyBean KnowledgeIndexRepository indexes;
  @MockitoSpyBean KnowledgeRepository documents;

  @BeforeEach
  void setup() {
    properties.setMode("HYBRID");
    properties.setEnhancementEnabled(false);
    properties.setEmbeddingModel("fixture-embedding");
    properties.setEmbeddingVersion("fixture-v1");
    properties.setEmbeddingDimension(2);
    properties.setTimeoutMillis(8000);
    jdbc.sql("UPDATE knowledge_index_head SET index_version=NULL").update();
    jdbc.sql("DELETE FROM knowledge_index_chunk").update();
    jdbc.sql("DELETE FROM knowledge_index_version").update();
    when(embedding.embed(anyList(), anyLong()))
        .thenAnswer(
            call ->
                ((List<?>) call.getArgument(0)).stream().map(t -> new double[] {1, 0.2}).toList());
  }

  private KnowledgeRetrievalRequest request(String original, String... queries) {
    return new KnowledgeRetrievalRequest(
        original, queries.length == 0 ? null : List.of(queries), null, null, null);
  }

  private KnowledgeExecutionContext context(String original) {
    return new KnowledgeExecutionContext(UUID.randomUUID().toString(), original, List.of(), 30000);
  }

  private String head() {
    return jdbc.sql("SELECT index_version FROM knowledge_index_head WHERE head_id=1")
        .query(String.class)
        .single();
  }

  @Test
  void preservesPairsAndOriginalSubsets() {
    indexing.rebuild();
    String original = "苹果退货、Nike配送";
    var result = knowledge.retrieve(request(original, "Apple退货", "Nike配送"), context(original));
    assertThat(result.getStatus()).isEqualTo("FOUND");
    assertThat(result.getCoverage().getRequested()).isEqualTo(2);
    assertThat(result.getHits())
        .extracting(h -> h.getBrand() + "/" + h.getDocumentType())
        .containsExactly("APPLE/RETURNS", "NIKE/SHIPPING");
    assertThat(knowledge.retrieve(original, null, null, null).getHits())
        .extracting(h -> h.getBrand() + "/" + h.getDocumentType())
        .containsExactly("APPLE/RETURNS", "NIKE/SHIPPING");
    assertThat(result.getHits())
        .allSatisfy(
            h -> {
              assertThat(h.getRetrievalChannels()).containsExactly("DB_KEYWORD", "VECTOR");
              assertThat(h.getIndexVersion()).isEqualTo(head());
              assertThat(h.getFusionScore()).isCloseTo(2.0 / 61, within(1e-9));
              assertThat(h.getSourceUrl()).startsWith("https://");
              assertThat(h.getScore()).isNotNegative();
            });
  }

  @Test
  void returnsRealMissingCategoryAndUnknownBrandGaps() {
    properties.setMode("KEYWORD");
    String original = "Apple退货、Nike保修";
    var result = knowledge.retrieve(request(original, "Apple退货", "Nike保修"), context(original));
    assertThat(result.getStatus()).isEqualTo("PARTIAL");
    assertThat(result.getCoverage().getMissingQueryIds()).hasSize(1);
    assertThat(result.getGroups().get(1).getStatus()).isEqualTo("NO_MATCH");
    String unknown = "苹果退货、华为保修";
    result = knowledge.retrieve(request(unknown, "苹果退货"), context(unknown));
    assertThat(result.getStatus()).isEqualTo("PARTIAL");
    assertThat(result.getCoverage().getMissingQueryIds()).isNotEmpty();
    assertThat(result.getGroups()).anyMatch(g -> g.getStatus().equals("CLARIFICATION_REQUIRED"));
  }

  @Test
  void mixedBusinessTaskDoesNotHidePolicyGaps() {
    properties.setMode("KEYWORD");
    String original = "Apple退货、Nike配送，再查我的订单状态";
    var result = knowledge.retrieve(request(original, "Apple退货"), context(original));
    assertThat(result.getStatus()).isEqualTo("PARTIAL");
    assertThat(result.getCoverage().getRequested()).isEqualTo(2);
    assertThat(result.getGroups()).noneMatch(g -> g.getIntent().getCategories().contains("ORDER"));
    assertThat(knowledge.retrieve(request("退货条件", "Apple退货"), context("退货条件")).getHits()).isEmpty();
    assertThat(knowledge.retrieve("退货条件", "APPLE", "RETURNS", null).getStatus()).isEqualTo("FOUND");
    assertThat(knowledge.retrieve(request("美国Apple退货", "Apple退货"), context("美国Apple退货")).getHits())
        .isEmpty();
    String platform = "我在你们商城买的iPhone能退吗";
    assertThat(knowledge.retrieve(request(platform, "本站退货规则"), context(platform)).getHits())
        .allMatch(h -> h.getBrand().equals("PLATFORM"));
    var followup =
        new KnowledgeExecutionContext("rewritten", "它能退吗", List.of("user:我买了iPhone"), 10000);
    assertThat(knowledge.retrieve(request("它能退吗", "Apple退货"), followup).getHits()).isNotEmpty();
    assertThat(knowledge.retrieve("美国Apple退货", null, null, null).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(knowledge.retrieve(request("Apple退货", "退货"), context("Apple退货")).getHits())
        .isEmpty();
  }

  @Test
  void cannotDropOriginalBrandChannelOrState() {
    properties.setMode("KEYWORD");
    String original = "苹果退货、Nike配送";
    var result = knowledge.retrieve(request(original, "苹果退货"), context(original));
    assertThat(result.getStatus()).isEqualTo("PARTIAL");
    assertThat(result.getCoverage().getRequested()).isEqualTo(2);
    assertThat(knowledge.retrieve(request("iPhone退货", "小米退货"), context("iPhone退货")).getHits())
        .isEmpty();
    assertThat(
            knowledge
                .retrieve(request("我在你们商城买的iPhone能退吗", "iPhone退货"), context("我在你们商城买的iPhone能退吗"))
                .getHits())
        .isEmpty();
    assertThat(knowledge.retrieve(request("苹果官网已激活还能退吗", "苹果退货"), context("苹果官网已激活还能退吗")).getHits())
        .isEmpty();
  }

  @Test
  void resolvesTraceableHistoryButAsksForAmbiguousSubject() {
    properties.setMode("KEYWORD");
    String q = "它能退吗";
    var single = new KnowledgeExecutionContext("one", q, List.of("user:我刚买了iPhone"), 10000);
    var result = knowledge.retrieve(request(q), single);
    assertThat(result.getHits()).allMatch(h -> h.getBrand().equals("APPLE"));
    assertThat(result.getGroups().get(0).getResolvedContextReferences()).isNotEmpty();
    var ambiguous = new KnowledgeExecutionContext("two", q, List.of("user:比较iPhone和小米手机"), 10000);
    assertThat(knowledge.retrieve(request(q), ambiguous).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
  }

  @Test
  void degradedModesAreNotContentAbsence() {
    indexing.rebuild();
    when(embedding.embed(anyList(), anyLong()))
        .thenThrow(new IllegalStateException("fixture outage"));
    var result = knowledge.retrieve("苹果退货", null, null, null);
    assertThat(result.getStatus()).isEqualTo("FOUND");
    assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD");
    assertThat(result.getWarnings()).anyMatch(w -> w.contains("VECTOR_FAILED"));
  }

  @Test
  void keywordFailureStillAllowsVectorButTwoFailuresAreErrors() throws Exception {
    indexing.rebuild();
    doThrow(new IllegalStateException("SQL outage"))
        .when(indexes)
        .search(anyString(), anyString(), anyString(), anyString(), anyInt());
    var result = knowledge.retrieve("苹果退货", null, null, null);
    assertThat(result.getRetrievalMode()).isEqualTo("VECTOR");
    assertThat(result.getHits()).isNotEmpty();
    when(embedding.embed(anyList(), anyLong()))
        .thenThrow(new IllegalStateException("model outage"));
    assertThatIllegalStateException()
        .isThrownBy(() -> knowledge.retrieve("苹果退货", null, null, null))
        .withMessageContaining("基础设施");
    mvc.perform(
            post("/api/store/knowledge/search")
                .contentType("application/json")
                .content("{\"query\":\"苹果退货\"}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void partialGroupOutageKeepsOtherEvidence() {
    indexing.rebuild();
    doThrow(new IllegalStateException("SQL outage"))
        .when(indexes)
        .search(anyString(), anyString(), eq("NIKE"), anyString(), anyInt());
    when(embedding.embed(anyList(), anyLong()))
        .thenAnswer(
            call -> {
              List<String> texts = call.getArgument(0);
              if (texts.stream().anyMatch(q -> q.contains("Nike")))
                throw new IllegalStateException("model outage");
              return texts.stream().map(q -> new double[] {1, 0.2}).toList();
            });
    var result = knowledge.retrieve(request("苹果退货、Nike配送", "苹果退货", "Nike配送"), null);
    assertThat(result.getStatus()).isEqualTo("PARTIAL");
    assertThat(result.getHits()).allMatch(h -> h.getBrand().equals("APPLE"));
    assertThat(result.getGroups().get(1).getStatus()).isEqualTo("ERROR");
  }

  @Test
  void failedBuildCannotReplacePublishedVersion() {
    String old = indexing.rebuild();
    when(embedding.embed(anyList(), anyLong()))
        .thenThrow(new IllegalStateException("provider outage"));
    assertThatIllegalStateException().isThrownBy(indexing::rebuild);
    assertThat(head()).isEqualTo(old);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM knowledge_index_version WHERE build_status='FAILED'")
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void sourceChangeDuringNetworkBuildCannotBePublished() {
    String old = indexing.rebuild();
    var changed = new java.util.concurrent.atomic.AtomicBoolean();
    when(embedding.embed(anyList(), anyLong()))
        .thenAnswer(
            call -> {
              if (changed.compareAndSet(false, true))
                jdbc.sql(
                        "UPDATE knowledge_document SET enabled=FALSE WHERE"
                            + " document_key='apple-returns'")
                    .update();
              return ((List<?>) call.getArgument(0))
                  .stream().map(t -> new double[] {1, 0.2}).toList();
            });
    try {
      assertThatIllegalStateException().isThrownBy(indexing::rebuild).withMessageContaining("原文变化");
      assertThat(head()).isEqualTo(old);
    } finally {
      jdbc.sql("UPDATE knowledge_document SET enabled=TRUE WHERE document_key='apple-returns'")
          .update();
    }
  }

  @Test
  void sourceChangeAndModelIdentityInvalidateOldVectors() {
    indexing.rebuild();
    String old;
    // 使用真实 key 读取，避免测试绑定资源命名细节。
    var hit = documents.search("苹果退货", "APPLE", "RETURNS", 1).get(0);
    String key = hit.getDocumentKey();
    old = hit.getContent();
    try {
      jdbc.sql("UPDATE knowledge_document SET content=:content WHERE document_key=:key")
          .param("content", old + " 测试源版本变化。")
          .param("key", key)
          .update();
      assertThatIllegalStateException().isThrownBy(indexing::snapshot).withMessageContaining("变化");
      var result = knowledge.retrieve("苹果退货", null, null, null);
      assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD");
      assertThat(result.getWarnings()).anyMatch(w -> w.contains("VECTOR_INDEX_UNAVAILABLE"));
    } finally {
      jdbc.sql("UPDATE knowledge_document SET content=:content WHERE document_key=:key")
          .param("content", old)
          .param("key", key)
          .update();
    }
    properties.setEmbeddingModel("another-model-same-dimension");
    assertThatIllegalStateException().isThrownBy(indexing::snapshot).withMessageContaining("不兼容");
  }

  @Test
  void usesRequestCacheButNewQuestionsAndVersionsAreNotSkipped() {
    indexing.rebuild();
    clearInvocations(embedding);
    properties.setEnhancementEnabled(true);
    when(enhancer.enhance(anyMap(), anyList(), anyLong()))
        .thenAnswer(
            call -> {
              Map<String, String> qs = call.getArgument(0);
              var result = new LinkedHashMap<String, List<String>>();
              qs.forEach((id, q) -> result.put(id, List.of(q, q)));
              return result;
            });
    String original = "苹果退货、Nike配送";
    var ctx = context(original);
    var first = request(original, "苹果退货");
    knowledge.retrieve(first, ctx);
    knowledge.retrieve(first, ctx);
    verify(enhancer, times(1)).enhance(anyMap(), anyList(), anyLong());
    verify(embedding, times(1)).embed(anyList(), anyLong());
    knowledge.retrieve(request(original, "Nike配送"), ctx);
    verify(enhancer, times(2)).enhance(anyMap(), anyList(), anyLong());
    indexing.rebuild();
    clearInvocations(embedding);
    knowledge.retrieve(first, ctx);
    verify(embedding, times(1)).embed(anyList(), anyLong());
  }

  @Test
  void rejectsInventedRewritesAndFallsBackOnTimeout() {
    indexing.rebuild();
    properties.setEnhancementEnabled(true);
    when(enhancer.enhance(anyMap(), anyList(), anyLong()))
        .thenReturn(Map.of("q1", List.of("小米退货", "小米保修")));
    var result = knowledge.retrieve("苹果退货", null, null, null);
    assertThat(result.getWarnings()).anyMatch(w -> w.contains("ENHANCEMENT_REJECTED"));
    assertThat(result.getHits()).allMatch(h -> h.getBrand().equals("APPLE"));
    for (String candidate : List.of("苹果不支持退货", "苹果美国退货", "苹果7天退货", "苹果七天退货", "苹果无条件退货")) {
      when(enhancer.enhance(anyMap(), anyList(), anyLong()))
          .thenReturn(Map.of("q1", List.of(candidate, candidate)));
      assertThat(knowledge.retrieve("苹果退货", null, null, null).getWarnings())
          .anyMatch(w -> w.contains("ENHANCEMENT_REJECTED"));
    }
    when(enhancer.enhance(anyMap(), anyList(), anyLong()))
        .thenThrow(new IllegalStateException("timeout"));
    assertThat(knowledge.retrieve("苹果退货", null, null, null).getWarnings())
        .anyMatch(w -> w.contains("ENHANCEMENT_FALLBACK"));
  }

  @Test
  void deadlineCancelsBackgroundModelWork() throws Exception {
    indexing.rebuild();
    properties.setTimeoutMillis(200);
    var cancelled = new java.util.concurrent.CountDownLatch(1);
    when(embedding.embed(anyList(), anyLong()))
        .thenAnswer(
            call -> {
              try {
                Thread.sleep(3000);
              } catch (InterruptedException e) {
                cancelled.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("cancelled");
              }
              return List.of(new double[] {1, 0.2});
            });
    long start = System.nanoTime();
    var result = knowledge.retrieve("苹果退货", null, null, null);
    assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
        .isLessThan(2000);
    assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD");
    assertThat(cancelled.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void sourceDisabledAndCorruptVectorsCannotLeakIntoResults() {
    indexing.rebuild();
    jdbc.sql("UPDATE knowledge_document SET enabled=FALSE WHERE document_key='apple-returns'")
        .update();
    try {
      assertThatIllegalStateException().isThrownBy(indexing::snapshot);
      assertThat(knowledge.retrieve("苹果退货", null, null, null).getHits()).isEmpty();
    } finally {
      jdbc.sql("UPDATE knowledge_document SET enabled=TRUE WHERE document_key='apple-returns'")
          .update();
    }
    jdbc.sql(
            "UPDATE knowledge_index_chunk SET vector_json='[0,0]' WHERE"
                + " document_key='apple-returns'")
        .update();
    assertThatIllegalStateException().isThrownBy(indexing::snapshot);
  }

  @Test
  void wholeEvidencePackingRecomputesCoverageAndUnion() throws Exception {
    properties.setMode("KEYWORD");
    var result = knowledge.retrieve("苹果 小米 Nike 宜家 MUJI 退货", null, null, null);
    var packed = json.valueToTree(KnowledgeEvidencePacker.pack(result, json, 4000));
    assertThat(json.writeValueAsString(Map.of("ok", true, "data", packed)).length())
        .isLessThanOrEqualTo(4000);
    assertThat(packed.path("status").asText()).isEqualTo("PARTIAL");
    assertThat(packed.path("coverage").path("missingQueryIds").size()).isPositive();
    var union = new java.util.HashSet<String>();
    for (var g : packed.path("groups"))
      for (var h : g.path("hits")) union.add(h.path("documentKey").asText());
    assertThat(packed.path("hits").size()).isEqualTo(union.size());
    for (var h : packed.path("hits")) {
      var original =
          result.getHits().stream()
              .filter(hit -> hit.getDocumentKey().equals(h.path("documentKey").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(h.path("content").asText()).isEqualTo(original.getContent());
      assertThat(h.path("sourceUrl").asText()).isEqualTo(original.getSourceUrl());
    }
  }

  @Test
  void postRejectsMalformedArraysAndExposesGroups() throws Exception {
    properties.setMode("KEYWORD");
    mvc.perform(
            post("/api/store/knowledge/search")
                .contentType("application/json")
                .content("{\"query\":\"苹果退货、Nike配送\",\"queries\":[\"苹果退货\",\"Nike配送\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups.length()").value(2))
        .andExpect(jsonPath("$.coverage.answeredWithEvidence").value(2));
    mvc.perform(
            post("/api/store/knowledge/search")
                .contentType("application/json")
                .content("{\"query\":\"苹果退货\",\"queries\":[2]}"))
        .andExpect(status().isBadRequest());
  }
}
