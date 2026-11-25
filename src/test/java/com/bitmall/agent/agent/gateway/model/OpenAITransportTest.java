package com.bitmall.agent.agent.gateway.model;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.openai.*;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tools.jackson.databind.*;

/** 本机模型协议替身，验证 Spring AI 2 真实 SSE、拆分参数、结果回传及禁用自动工具执行。 */
class OpenAITransportTest {
  @Test
  void openAICompatibleSseTransmitsSchemaToolArgumentsAndResultsAndCanBeReused() throws Exception {
    ObjectMapper json = new ObjectMapper();
    List<JsonNode> requests = new CopyOnWriteArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          requests.add(json.readTree(exchange.getRequestBody()));
          boolean first = requests.size() == 1;
          List<Map<String, Object>> deltas =
              first
                  ? List.of(
                      Map.of(
                          "role",
                          "assistant",
                          "tool_calls",
                          List.of(
                              Map.of(
                                  "index",
                                  0,
                                  "id",
                                  "call-date",
                                  "type",
                                  "function",
                                  "function",
                                  Map.of("name", "lookup", "arguments", "{\"query\":")))),
                      Map.of(
                          "tool_calls",
                          List.of(
                              Map.of("index", 0, "function", Map.of("arguments", "\"优惠规则\"}")))))
                  : List.of(
                      Map.of("role", "assistant", "content", "已查到"), Map.of("content", "规则。"));
          StringBuilder body = new StringBuilder();
          for (int i = 0; i < deltas.size(); i++) {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("delta", deltas.get(i));
            choice.put(
                "finish_reason", i == deltas.size() - 1 ? (first ? "tool_calls" : "stop") : null);
            body.append("data: ")
                .append(
                    json.writeValueAsString(
                        Map.of(
                            "id",
                            "test-response",
                            "object",
                            "chat.completion.chunk",
                            "created",
                            1,
                            "model",
                            "local-stub",
                            "choices",
                            List.of(choice))))
                .append("\n\n");
          }
          body.append("data: [DONE]\n\n");
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().write(body.toString().getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      var model =
          OpenAiChatModel.builder()
              .options(
                  OpenAiChatOptions.builder()
                      .apiKey("local-test-placeholder")
                      .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                      .model("local-stub")
                      .maxRetries(0)
                      .build())
              .build();
      var client = ChatClient.builder(model).build();
      var callback =
          FunctionToolCallback.builder("lookup", (String query) -> "订单满足门槛可使用优惠券")
              .description("查规则")
              .inputType(String.class)
              .inputSchema(
                  "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
              .build();
      List<Message> history = new ArrayList<>();
      history.add(new UserMessage("查询规则"));
      for (int i = 0; i < 3; i++) {
        List<String> deltas = new ArrayList<>();
        var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        ChatResponse completed;
        try (AgentExecution execution = new AgentExecution(timer, 15)) {
          execution.bind();
          completed =
              new SpringAiStreamReader(client)
                  .read(history, List.of(callback), execution, deltas::add);
        } finally {
          timer.shutdownNow();
        }
        assertThat(requests).hasSize(i + 1); // 由普通循环驱动下一轮，SDK 不自动执行工具。
        var answer = completed.getResult().getOutput();
        if (i == 0) {
          assertThat(answer.getToolCalls()).hasSize(1);
          assertThat(json.readTree(answer.getToolCalls().get(0).arguments()).path("query").asText())
              .isEqualTo("优惠规则");
          history.add(answer);
          history.add(
              ToolResponseMessage.builder()
                  .responses(
                      List.of(
                          new ToolResponseMessage.ToolResponse(
                              "call-date", "lookup", "订单满足门槛可使用优惠券")))
                  .build());
        } else {
          assertThat(answer.getText()).isEqualTo("已查到规则。");
          assertThat(deltas).hasSizeGreaterThanOrEqualTo(2);
        }
      }
      assertThat(requests.get(0).at("/tools/0/function/name").asText()).isEqualTo("lookup");
      assertThat(requests.get(0).path("stream").asBoolean()).isTrue();
      assertThat(requests.get(1).path("messages").toString())
          .contains("tool_call_id", "call-date", "订单满足门槛");
    } finally {
      server.stop(0);
    }
  }
}
