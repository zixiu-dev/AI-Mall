package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.agent.service.chat.AgentChatService;
import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.knowledge.service.retrieval.EmbeddingClient;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeRetriever;
import com.bitmall.agent.knowledge.service.retrieval.QueryEnhancer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 显式启用才连接付费真实模型；没有凭据时跳过，不用替身冒充真实语义验收。 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:rag_real;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.knowledge.mode=HYBRID",
      "app.agent.ai-enabled=true",
      // 限定真实验收目的地。数据来自本类全新 H2 库的仓库种子，不读取运行中的用户会话。
      "app.agent.model.base-url=https://api.siliconflow.cn",
      "app.agent.model.endpoint-path=/v1/chat/completions",
      "app.agent.model.name=deepseek-ai/DeepSeek-V4-Flash",
      "app.knowledge.embedding-base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
      "app.knowledge.embedding-path=/embeddings",
      "app.knowledge.embedding-model=text-embedding-v4",
      "app.knowledge.embedding-dimension=1024",
      "app.catalog-cache.enabled=false",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RAG_REAL_TESTS", matches = "true")
class RealRagIntegrationTest {
  @Autowired QueryEnhancer enhancer;
  @Autowired EmbeddingClient embedding;
  @Autowired KnowledgeIndexService index;
  @Autowired KnowledgeService knowledge;
  @Autowired KnowledgeProperties properties;
  @Autowired AgentChatService agent;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;

  /** 真实模型验收必须显式指向可丢弃 PG 测试库，禁止沿用运行中应用的发布指针。 */
  @DynamicPropertySource
  static void isolatedVectorDatabase(DynamicPropertyRegistry registry) {
    registry.add("app.knowledge.pgvector.url", () -> required("PGVECTOR_TEST_URL"));
    registry.add("app.knowledge.pgvector.username", () -> required("PGVECTOR_TEST_USERNAME"));
    registry.add("app.knowledge.pgvector.password", () -> required("PGVECTOR_TEST_PASSWORD"));
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException(
          "真实 PG 模型测试需独立测试库，请使用 scripts/verify-pgvector.py --real-model");
    return value;
  }

  private String version;

  @BeforeAll
  void build() {
    version = index.rebuild();
  }

  @Test
  void realSemanticNeighborsAndHybridEvidence() throws Exception {
    long start = System.nanoTime();
    var vectors =
        embedding.embed(
            List.of("手机包装的封条已经揭开，退回时需带齐原配件。", "货物通过快递运输，收件地址不同会影响邮费。", "买的手机已经开盒，还能寄回商家吗？"),
            System.nanoTime() + TimeUnit.SECONDS.toNanos(8));
    double related = KnowledgeRetriever.cosine(vectors.get(2), vectors.get(0)),
        unrelated = KnowledgeRetriever.cosine(vectors.get(2), vectors.get(1));
    assertThat(related).isGreaterThan(unrelated);
    var result = knowledge.retrieve("苹果官网买的手机已经开盒，还能退吗", null, null, null);
    assertThat(result.getRetrievalMode()).isEqualTo("DB_KEYWORD+VECTOR");
    assertThat(result.getHits()).isNotEmpty();
    assertThat(result.getHits())
        .allSatisfy(
            h -> {
              assertThat(h.getIndexVersion()).isEqualTo(version);
              assertThat(h.getEmbeddingModelId()).isEqualTo(properties.getEmbeddingModel());
              assertThat(h.getRetrievalChannels()).contains("VECTOR");
            });
    report(
        "semantic",
        Map.of(
            "model",
            properties.getEmbeddingModel(),
            "versionTag",
            properties.getEmbeddingVersion(),
            "indexVersion",
            version,
            "relatedCosine",
            related,
            "unrelatedCosine",
            unrelated,
            "latencyMs",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
            "result",
            result));
  }

  @Test
  void liveBatchEnhancementUsesStrictStructure() throws Exception {
    long start = System.nanoTime();
    var output =
        enhancer.enhance(
            Map.of("q1", "Apple官网退货条件"),
            List.of(),
            System.nanoTime() + TimeUnit.SECONDS.toNanos(8));
    assertThat(output.keySet()).containsExactly("q1");
    assertThat(output.get("q1")).hasSize(2).allMatch(q -> !q.isBlank());
    report(
        "enhancement",
        Map.of(
            "latencyMs",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
            "output",
            output));
  }

  @Test
  void liveModelReturnsKnowledgeThenUsesBusinessTool() throws Exception {
    long start = System.nanoTime();
    var reply =
        agent.chat(10001, null, "请对比 Apple 官网与 Nike 官网的退货条件，再查询我最近的订单状态。只查询，不申请或购买。回答附政策来源。");
    assertThat(reply.getPendingAction()).isNull();
    assertThat(reply.getContent()).contains("https://");
    var calls = new ArrayList<String>();
    for (String payload :
        jdbc.sql("SELECT payload FROM agent_scope_state WHERE session_id=:s")
            .param("s", reply.getConversationId())
            .query(String.class)
            .list()) collect(json.readTree(payload), calls);
    assertThat(calls).contains("search_knowledge", "order_query");
    report(
        "agent",
        Map.of(
            "indexVersion",
            version,
            "latencyMs",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
            "toolSequence",
            calls,
            "reply",
            reply.getContent(),
            "tokenUsage",
            "SDK 持久化状态未提供统一 usage，需结合模型网关账单核对"));
  }

  private void collect(JsonNode node, List<String> calls) {
    if (node.isObject() && node.has("name") && node.has("arguments"))
      calls.add(node.path("name").asText());
    for (JsonNode child : node) collect(child, calls);
  }

  private void report(String name, Object data) throws Exception {
    Path dir = Path.of(".runtime/spring-ai");
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve("real-" + name + ".json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(data));
  }
}
