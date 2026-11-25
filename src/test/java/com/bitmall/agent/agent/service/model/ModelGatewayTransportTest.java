package com.bitmall.agent.agent.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bitmall.agent.agent.config.SpringAiConfiguration;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.runtime.SpringAiStreamReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 本机 OpenAI 兼容 HTTP 桩覆盖实际 SDK 的状态码、截断流、候选切换和用量。 */
class ModelGatewayTransportTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void incompleteStreamAndHttp503OpenPrimaryThenNewRequestsSkipIt() throws Exception {
    List<JsonNode> primaryRequests = new CopyOnWriteArrayList<>();
    List<JsonNode> backupRequests = new CopyOnWriteArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/primary/chat/completions", exchange -> {
      primaryRequests.add(json.readTree(exchange.getRequestBody()));
      if (primaryRequests.size() == 1) {
        stream(exchange, "半截草稿", false);
      } else {
        error(exchange, 503);
      }
    });
    server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
      backupRequests.add(json.readTree(exchange.getRequestBody()));
      assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-key");
      stream(exchange, "备用结果", true);
    });
    server.start();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    var metrics = new SimpleMeterRegistry();
    try {
      var gateway = gateway(server, metrics);
      var events = new ArrayList<String>();
      var usedTools = new AtomicInteger();
      var tool = FunctionToolCallback.builder("lookup", (String query) -> usedTools.incrementAndGet())
          .description("查询商品")
          .inputType(String.class)
          .inputSchema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}")
          .build();
      List<Message> history = List.of(new UserMessage("查询商品"),
          AssistantMessage.builder().toolCalls(List.of(
              new AssistantMessage.ToolCall("lookup-1", "function", "lookup", "{}"))).build(),
          ToolResponseMessage.builder().responses(List.of(
              new ToolResponseMessage.ToolResponse("lookup-1", "lookup", "商品已查询"))).build());
      for (int i = 0; i < 3; i++) {
        try (var execution = new AgentExecution(scheduler, 10)) {
          execution.bind();
          var response = gateway.generate(history, List.of(tool), execution,
              events::add, () -> events.add("[reset]"));
          assertThat(response.getResult().getOutput().getText()).isEqualTo("备用结果");
        }
      }
      assertThat(events).containsExactly("半截草稿", "[reset]", "备用结果", "备用结果", "备用结果");
      assertThat(primaryRequests).hasSize(2);
      assertThat(backupRequests).hasSize(3);
      assertThat(backupRequests.get(0).path("model").asText()).isEqualTo("qwen-plus");
      assertThat(backupRequests.get(0).path("messages")).isEqualTo(primaryRequests.get(0).path("messages"));
      assertThat(backupRequests.get(0).path("messages").get(2).path("tool_call_id").asText())
          .isEqualTo("lookup-1");
      assertThat(backupRequests.get(0).path("tools")).isEqualTo(primaryRequests.get(0).path("tools"));
      assertThat(usedTools.get()).isZero();
      assertThat(metrics.get("agent.model.circuit.state").tag("candidate", "primary").gauge().value()).isEqualTo(1);
      assertThat(metrics.get("agent.model.skipped").tag("candidate", "primary").counter().count()).isEqualTo(1);
      assertThat(metrics.get("agent.model.tokens").tags("candidate", "bailian", "kind", "input").counter().count())
          .isEqualTo(21);
      assertThat(metrics.get("agent.model.usage.missing").tag("candidate", "primary").counter().count()).isEqualTo(2);
    } finally {
      metrics.close();
      server.stop(0);
      scheduler.shutdownNow();
    }
  }

  @Test
  void enablingBailianKeepsUnqualifiedChatClientOnPrimary() {
    new ApplicationContextRunner()
        .withUserConfiguration(SpringAiConfiguration.class)
        .withPropertyValues(
            "app.agent.model.api-key=local-main-key",
            "app.agent.model.base-url=http://127.0.0.1:1",
            "app.agent.model.endpoint-path=/v1/chat/completions",
            "app.agent.model.name=local-main",
            "app.agent.model.bailian.enabled=true",
            "app.agent.model.bailian.api-key=local-test-key",
            "app.agent.model.bailian.base-url=http://127.0.0.1:1/compatible-mode/v1",
            "app.agent.model.bailian.name=qwen-plus")
        .run(context -> {
          assertThat(context).hasBean("bailianChatClient");
          assertThat(context.getBean(ChatClient.class)).isSameAs(context.getBean("commerceChatClient"));
        });
  }

  @Test
  void badRequestNeitherCallsBackupNorCountsTowardCircuit() throws Exception {
    var primaryCalls = new AtomicInteger();
    var backupCalls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/primary/chat/completions", exchange -> {
      primaryCalls.incrementAndGet();
      error(exchange, 400);
    });
    server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
      backupCalls.incrementAndGet();
      stream(exchange, "备用结果", true);
    });
    server.start();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    var metrics = new SimpleMeterRegistry();
    try {
      var gateway = gateway(server, metrics);
      for (int i = 0; i < 3; i++) {
        try (var execution = new AgentExecution(scheduler, 10)) {
          execution.bind();
          assertThatThrownBy(() -> gateway.generate(List.of(new UserMessage("查询商品")), List.of(),
              execution, ignored -> {}, () -> {})).isInstanceOf(ModelUnavailableException.class);
        }
      }
      assertThat(primaryCalls.get()).isEqualTo(3);
      assertThat(backupCalls.get()).isZero();
    } finally {
      metrics.close();
      server.stop(0);
      scheduler.shutdownNow();
    }
  }

  private ModelGateway gateway(HttpServer server, SimpleMeterRegistry metrics) {
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    var config = new SpringAiConfiguration();
    var primary = ChatClient.builder(
        config.commerceChatModel("local-test-key", url, "/primary/chat/completions", "local-main")).build();
    var backup = config.bailianChatClient("local-test-key", url + "/compatible-mode/v1", "qwen-plus");
    return new ModelGateway(new SpringAiStreamReader(primary), Optional.of(backup), metrics,
        2, Duration.ofSeconds(30));
  }

  private void error(HttpExchange exchange, int status) throws IOException {
    send(exchange, status, "application/json", "{\"error\":{\"message\":\"模拟 HTTP 错误\"}}");
  }

  private void stream(HttpExchange exchange, String text, boolean completed) throws IOException {
    Map<String, Object> choice = new java.util.LinkedHashMap<>();
    choice.put("index", 0);
    choice.put("delta", Map.of("role", "assistant", "content", text));
    choice.put("finish_reason", completed ? "stop" : null);
    String first = chunk(List.of(choice), Map.of());
    String end = completed ? chunk(List.of(), Map.of("usage",
        Map.of("prompt_tokens", 7, "completion_tokens", 3, "total_tokens", 10))) : "";
    send(exchange, 200, "text/event-stream", first + end + "data: [DONE]\n\n");
  }

  private String chunk(List<?> choices, Map<String, Object> extras) {
    Map<String, Object> fields = new java.util.HashMap<>(extras);
    fields.putAll(Map.of("id", "stub", "object", "chat.completion.chunk", "created", 1,
        "model", "local-test", "choices", choices));
    return "data: " + json.writeValueAsString(fields) + "\n\n";
  }

  private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (exchange) {
      exchange.getResponseBody().write(bytes);
    }
  }
}
