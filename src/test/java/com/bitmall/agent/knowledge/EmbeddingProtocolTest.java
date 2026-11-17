package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.knowledge.config.KnowledgeIndexCommand;
import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.service.retrieval.SpringAiEmbeddingClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 本机 HTTP 协议替身验证网络适配和离线命令；不把它称为百炼真实语义模型验收。 */
class EmbeddingProtocolTest {
  private HttpServer server;
  private final ObjectMapper json = new ObjectMapper();
  private KnowledgeProperties properties;
  private java.util.function.Function<tools.jackson.databind.JsonNode, Object> response;
  private SpringAiEmbeddingClient client;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    properties = new KnowledgeProperties();
    properties.setEmbeddingBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setEmbeddingPath("/embeddings");
    properties.setEmbeddingModel("fixture-model");
    properties.setEmbeddingVersion("v1");
    properties.setEmbeddingDimension(2);
    properties.setEmbeddingApiKey("fixture-secret");
    response =
        body -> {
          var data = new ArrayList<Object>();
          for (int i = body.path("input").size() - 1; i >= 0; i--)
            data.add(Map.of("index", i, "embedding", List.of(i + 1.0, 0.5)));
          return Map.of("model", "fixture-model", "data", data);
        };
    server.createContext(
        "/embeddings",
        exchange -> {
          try {
            var body = json.readTree(exchange.getRequestBody());
            assertThat(body.path("dimensions").asInt()).isEqualTo(2);
            var responseBody =
                new java.util.LinkedHashMap<>((Map<String, Object>) response.apply(body));
            responseBody.put("object", "list");
            responseBody.put("usage", Map.of("prompt_tokens", 3, "total_tokens", 3));
            var bytes = json.writeValueAsBytes(responseBody);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } finally {
            exchange.close();
          }
        });
    server.start();
    client = new SpringAiEmbeddingClient(properties);
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  private long deadline() {
    return System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
  }

  @Test
  void restoresInputOrderAndChecksFiniteVectors() {
    var vectors = client.embed(List.of("苹果政策", "Nike政策"), deadline());
    assertThat(vectors.get(0)).containsExactly(1, 0.5);
    assertThat(vectors.get(1)).containsExactly(2, 0.5);
  }

  @Test
  void rejectsDifferentModelEvenWithSameDimension() {
    response =
        b ->
            Map.of(
                "model",
                "another-model",
                "data",
                List.of(Map.of("index", 0, "embedding", List.of(1, 0))));
    assertThatIllegalStateException()
        .isThrownBy(() -> client.embed(List.of("test"), deadline()))
        .withMessageNotContaining("fixture-secret");
  }

  @Test
  void rejectsDuplicatesWrongDimensionAndZeroVectors() {
    for (Object data :
        List.of(
            List.of(Map.of("index", 0, "embedding", List.of(1))),
            List.of(Map.of("index", 0, "embedding", List.of(0, 0))),
            List.of(Map.of("index", 1, "embedding", List.of(1, 0))))) {
      response = b -> Map.of("model", "fixture-model", "data", data);
      assertThatIllegalStateException().isThrownBy(() -> client.embed(List.of("test"), deadline()));
    }
  }

  @Test
  void standaloneCommandBuildsAndExitsWithoutCommerceRuntime() throws Exception {
    String database =
        "jdbc:h2:mem:rag_command;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    KnowledgeIndexCommand.main(
        new String[] {
          "--app.knowledge.vector-store=LEGACY",
          "--spring.datasource.url=" + database,
          "--spring.datasource.username=sa",
          "--spring.datasource.password=",
          "--spring.datasource.driver-class-name=org.h2.Driver",
          "--spring.flyway.locations=classpath:db/migration",
          "--app.knowledge.embedding-base-url=" + properties.getEmbeddingBaseUrl(),
          "--app.knowledge.embedding-path=/embeddings",
          "--app.knowledge.embedding-model=fixture-model",
          "--app.knowledge.embedding-version=v1",
          "--app.knowledge.embedding-dimension=2"
        });
    try (var connection = java.sql.DriverManager.getConnection(database, "sa", "");
        var statement = connection.createStatement();
        var rs =
            statement.executeQuery(
                "SELECT v.chunk_count,v.build_status FROM knowledge_index_head h JOIN"
                    + " knowledge_index_version v ON h.index_version=v.index_version")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getInt(1)).isEqualTo(22);
      assertThat(rs.getString(2)).isEqualTo("PUBLISHED");
    }
  }
}
