package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bitmall.agent.knowledge.service.KnowledgeService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 使用完整 V18 和真实 SQL 验证品牌隔离、意图、补问、来源和本平台执行边界。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:brand_policies;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class BrandPolicyIntegrationTest {
  @Autowired KnowledgeService knowledge;
  @Autowired JdbcClient jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @ParameterizedTest
  @CsvSource({
    "iPhone能退吗,APPLE,RETURNS",
    "MacBook保修多久,APPLE,WARRANTY",
    "米家冰箱保修,XIAOMI,WARRANTY",
    "Redmi退货政策,XIAOMI,RETURNS",
    "耐克退货运费,NIKE,RETURNS",
    "Nike会员包邮吗,NIKE,SHIPPING",
    "宜家食品能退吗,IKEA,RETURNS",
    "无印良品退货条件,MUJI,RETURNS",
    "MUJI退款多久到账,MUJI,REFUND"
  })
  void aliasesRouteToExactBrandAndCategory(String query, String brand, String type) {
    var result = knowledge.retrieve(query, null, null, null);
    assertThat(result.getStatus()).isIn("FOUND", "PARTIAL");
    assertThat(result.getHits()).isNotEmpty();
    assertThat(result.getHits()).allMatch(hit -> brand.equals(hit.getBrand()));
    assertThat(result.getHits()).anyMatch(hit -> type.equals(hit.getDocumentType()));
    assertThat(result.getHits())
        .allMatch(
            hit ->
                hit.getSourceUrl().startsWith("https://")
                    && "2026-11-17".equals(hit.getSourceCheckedAt()));
  }

  @Test
  void comparisonsKeepBrandsSeparate() {
    var result = knowledge.retrieve("苹果和耐克的退货政策比较", null, null, null);
    assertThat(result.getHits()).extracting(h -> h.getBrand()).containsExactly("APPLE", "NIKE");
    assertThat(result.getHits().get(0).getContent()).contains("14");
    assertThat(result.getHits().get(1).getContent()).contains("7");
  }

  @Test
  void explicitBrandCannotEraseConflictingUserQuestion() {
    var result = knowledge.retrieve("耐克退货政策", "APPLE", "RETURNS", null);
    assertThat(result.getStatus()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.getHits()).isEmpty();
  }

  @Test
  void mismatchedCategoryAndUnknownBrandDoNotFallBack() {
    assertThat(knowledge.retrieve("苹果退货政策", "APPLE", "WARRANTY", null).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(knowledge.retrieve("华为保修多久", null, null, null).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(knowledge.retrieve("Acme退货政策", null, null, null).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(knowledge.retrieve("苹果", null, null, null).getStatus())
        .isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(knowledge.retrieve("Nike保修多久", null, null, null).getStatus()).isEqualTo("NO_MATCH");
  }

  @Test
  void localStorePurchaseDoesNotInheritAppleStoreWindow() {
    var result = knowledge.retrieve("我在你们商城买的iPhone能退吗", null, null, null);
    assertThat(result.getIntent().getScope()).isEqualTo("PLATFORM");
    assertThat(result.getHits()).allMatch(hit -> "PLATFORM".equals(hit.getBrand()));
    assertThat(result.getHits()).noneMatch(hit -> hit.getContent().contains("14 个自然日"));
  }

  @Test
  void literalWildcardsCannotExpandBrandFilter() {
    var result = knowledge.retrieve("% _ 苹果退货政策", null, null, null);
    assertThat(result.getHits())
        .allMatch(hit -> "APPLE".equals(hit.getBrand()) && "RETURNS".equals(hit.getDocumentType()));
  }

  @Test
  void documentsMatchManifestAndRetireMisleadingLegacyKnowledge() throws Exception {
    var manifest = json.readTree(new ClassPathResource("knowledge/manifest.json").getInputStream());
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM knowledge_document WHERE enabled=TRUE")
                .query(Integer.class)
                .single())
        .isEqualTo(22);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM knowledge_document WHERE id<=6 AND enabled=TRUE")
                .query(Integer.class)
                .single())
        .isZero();
    for (var doc : manifest.path("documents")) {
      assertThat(new ClassPathResource("knowledge/" + doc.path("file").asText()).exists()).isTrue();
      assertThat(
              jdbc.sql("SELECT content_version FROM knowledge_document WHERE document_key=:key")
                  .param("key", doc.path("key").asText())
                  .query(String.class)
                  .single())
          .isEqualTo(doc.path("contentVersion").asText());
    }
  }

  @Test
  void readOnlyApiExposesResolvedIntentAndSource() throws Exception {
    mvc.perform(get("/api/store/knowledge/search").param("query", "苹果手机退货政策"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FOUND"))
        .andExpect(jsonPath("$.intent.brands[0]").value("APPLE"))
        .andExpect(
            jsonPath("$.hits[0].sourceUrl")
                .value("https://www.apple.com.cn/shop/help/returns_refund"));
    mvc.perform(get("/api/store/knowledge/search").param("query", "苹果退货").param("scope", "unknown"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void policyQueriesCannotWriteOrdersOrAfterSales() throws Exception {
    int before = jdbc.sql("SELECT COUNT(*) FROM after_sales").query(Integer.class).single();
    var body = json.writeValueAsString(Map.of("userId", 10001, "message", "苹果官网买的iPhone退货有哪些条件"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/agent/chat")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("Apple")))
        .andExpect(
            jsonPath("$.content")
                .value(
                    org.hamcrest.Matchers.containsString(
                        "https://www.apple.com.cn/shop/help/returns_refund")))
        .andExpect(jsonPath("$.pendingAction").doesNotExist());
    assertThat(jdbc.sql("SELECT COUNT(*) FROM after_sales").query(Integer.class).single())
        .isEqualTo(before);
  }
}
