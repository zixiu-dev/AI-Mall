package com.bitmall.agent.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;

import com.bitmall.agent.product.service.ProductService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 通过真实 HTTP 连接验证浏览器断开、重连和后台生成的边界，模型输出由测试控制。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.agent.ai-enabled=true",
      "app.catalog-cache.enabled=false",
      "app.hotkey.enabled=false",
      "management.server.port=0",
      "spring.datasource.url=jdbc:h2:mem:agent-resume-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("demo")
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class AgentResumeHttpIntegrationTest {
  @LocalServerPort int port;
  @LocalManagementPort int managementPort;
  @Autowired ObjectMapper json;
  @Autowired ControlledModel model;
  @MockitoSpyBean ProductService products;

  private final HttpClient client = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(3))
      .build();

  @Test
  void disconnectKeepsGeneratingAndNewConnectionReceivesSnapshotThenDelta() throws Exception {
    Sinks.Many<ChatResponse> outputs = model.prepare();
    int initialCalls = model.calls.get();
    String requestId = UUID.randomUUID().toString();
    JsonNode accepted = submit(requestId, "对比两款手机", 202);
    long messageId = accepted.path("messageId").asLong();
    String conversation = accepted.path("conversationId").asText();

    try (BufferedReader first = stream(messageId)) {
      assertThat(event(first, "snapshot").path("content").asText()).isEmpty();
      emit(outputs, "A");
      assertThat(event(first, "delta").path("content").asText()).isEqualTo("A");
    }
    emit(outputs, "B");
    assertThat(submit(requestId, "对比两款手机", 202).path("messageId").asLong()).isEqualTo(messageId);
    submit(requestId, "同一个请求换了问题", 409);
    assertThat(get("/api/agent/chat/turns/" + requestId, 200).path("messageId").asLong())
        .isEqualTo(messageId);
    get("/api/agent/messages/" + messageId + "?userId=99999", 400);
    get("/api/agent/messages/" + messageId + "/execution?userId=99999", 400);
    sendJson(request("/api/agent/messages/" + messageId + "/stream?userId=99999")
        .header("Accept", "text/event-stream, application/json").GET().build(), 400);
    assertThat(get("/api/agent/conversations/" + conversation + "/snapshot", 200)
        .path("activeMessageId").asLong()).isEqualTo(messageId);

    try (BufferedReader reconnected = reconnectWithDraft(messageId, "AB")) {
      emit(outputs, "C");
      assertThat(event(reconnected, "delta").path("content").asText()).isEqualTo("C");
      assertThat(outputs.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
      JsonNode done = event(reconnected, "done").path("reply");
      assertThat(done.path("content").asText()).isEqualTo("ABC");
      assertThat(done.path("messageId").asLong()).isEqualTo(messageId);
    }

    JsonNode saved = get("/api/agent/messages/" + messageId, 200);
    assertThat(saved.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(saved.path("content").asText()).isEqualTo("ABC");
    JsonNode history = get("/api/agent/conversations/" + conversation + "/snapshot", 200).path("messages");
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.get(1).path("id").asLong()).isEqualTo(messageId);
    assertThat(history.get(1).path("content").asText()).isEqualTo("ABC");
    try (BufferedReader finished = stream(messageId)) {
      assertThat(event(finished, "done").path("reply").path("content").asText()).isEqualTo("ABC");
    }
    assertThat(model.calls.get() - initialCalls).isEqualTo(1);
    JsonNode execution = executionEntries(messageId, 2);
    assertThat(execution.get(0).path("itemType").asText()).isEqualTo("QUEUE_WAIT");
    assertThat(execution.get(1).path("itemType").asText()).isEqualTo("MODEL_CALL");
    assertThat(execution.get(1).path("status").asText()).isEqualTo("SUCCESS");
    assertThat(execution.get(1).path("inputTokens").isMissingNode()).isTrue();

    // 管理页面及新增接口只在独立端口存在，并沿用已有轮次的归属和明细。
    var page = managementGet("/agent-execution", 200);
    assertThat(page.headers().firstValue("Content-Type")).hasValueSatisfying(
        value -> assertThat(value).startsWith("text/html"));
    assertThat(page.body()).contains("Agent 执行记录");
    assertThat(managementGet("/agent-execution/dashboard.js", 200).body()).contains("loadDetail");
    get("/agent-execution", 404);
    get("/agent-execution/api/turns?userId=10001", 404);
    get("/agent-execution/dashboard.js", 404);
    var list = json.readTree(managementGet("/agent-execution/api/turns?userId=10001", 200).body());
    assertThat(list.get(0).path("turnId").asLong()).isEqualTo(messageId);
    assertThat(list.get(0).path("createdAt").isTextual()).isTrue();
    assertThat(list.get(0).path("status").asText()).isEqualTo("COMPLETED");
    assertThat(list.get(0).size()).isEqualTo(4);
    var detail = managementGet("/agent-execution/api/turns/" + messageId + "?userId=10001", 200);
    assertThat(detail.headers().firstValue("Cache-Control")).hasValue("no-store");
    assertThat(json.readTree(detail.body()).path("entries")).isEqualTo(execution);
    assertThat(json.readTree(managementGet("/agent-execution/api/turns?userId=99999", 200).body())
        .isEmpty()).isTrue();
    managementGet("/agent-execution/api/turns/" + messageId + "?userId=99999", 404);
    managementGet("/agent-execution/api/turns?userId=0", 400);
  }

  @Test
  void failureAfterTextPersistsFailureInsteadOfPartialAnswer() throws Exception {
    Sinks.Many<ChatResponse> outputs = model.prepare();
    long messageId = submit(UUID.randomUUID().toString(), "查询商品", 202).path("messageId").asLong();
    try (BufferedReader stream = stream(messageId)) {
      event(stream, "snapshot");
      emit(outputs, "临时草稿");
      assertThat(event(stream, "delta").path("content").asText()).isEqualTo("临时草稿");
      assertThat(outputs.tryEmitError(new IllegalStateException("测试模型中断"))).isEqualTo(Sinks.EmitResult.OK);
      assertThat(event(stream, "error").path("content").asText()).contains("没有生成完成");
    }
    JsonNode saved = get("/api/agent/messages/" + messageId, 200);
    assertThat(saved.path("status").asText()).isEqualTo("FAILED");
    assertThat(saved.path("content").asText()).doesNotContain("临时草稿");
  }

  @Test
  void modelFailureAfterCouponProposalRestoresOriginalCardOnReconnect() throws Exception {
    var skill = model.prepare();
    var proposal = model.prepare();
    var followUp = model.prepare();
    long messageId = submit(UUID.randomUUID().toString(), "领取品质生活优惠券", 202)
        .path("messageId").asLong();
    String cardId;
    try (BufferedReader live = stream(messageId)) {
      event(live, "snapshot");
      tool(skill, "load_skill", "{\"skillCode\":\"coupon_ops\"}");
      event(live, "tool_end");
      tool(proposal, "coupon_claim", "{\"couponId\":3002}");
      event(live, "tool_end");
      assertThat(followUp.tryEmitError(new IllegalStateException("模型生成故障")))
          .isEqualTo(Sinks.EmitResult.OK);
      JsonNode reply = event(live, "done").path("reply");
      assertThat(reply.path("content").asText()).contains("AI 服务暂时不可用");
      assertThat(reply.path("pendingAction").path("toolName").asText()).isEqualTo("coupon_claim");
      cardId = reply.path("pendingAction").path("id").asText();
      assertThat(cardId).isNotBlank();
    }
    try (BufferedReader reconnected = stream(messageId)) {
      assertThat(event(reconnected, "done").path("reply").path("pendingAction").path("id").asText())
          .isEqualTo(cardId);
    }
    JsonNode entries = executionEntries(messageId, 7);
    JsonNode proposalEntry = entries.get(4);
    assertThat(proposalEntry.path("actionId").asText()).isEqualTo(cardId);
    assertThat(proposalEntry.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
    assertThat(entries.get(6).path("itemType").asText()).isEqualTo("CONFIRMATION_RECOVERED");
    assertThat(entries.get(6).path("actionId").asText()).isEqualTo(cardId);
  }

  @Test
  void timelineCountsActualToolDispatchesAndLinksTheOriginatingModel() throws Exception {
    doThrow(new TransientDataAccessResourceException("temporary database failure"))
        .doCallRealMethod().when(products).product(201L);
    var invalid = model.prepare();
    var retried = model.prepare();
    var answer = model.prepare();
    long messageId = submit(UUID.randomUUID().toString(), "查询商品执行明细测试", 202)
        .path("messageId").asLong();
    tool(invalid, "product_detail", "{\"productId\":\"不能写入监控表的用户输入\"}");
    tool(retried, "product_detail", "{\"productId\":201}");
    emit(answer, "已查到商品。");
    assertThat(answer.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
    try (BufferedReader subscription = stream(messageId)) {
      assertThat(event(subscription, "done").path("reply").path("content").asText())
          .isEqualTo("已查到商品。");
    }
    JsonNode entries = executionEntries(messageId, 6);
    JsonNode rejected = entries.get(2);
    assertThat(rejected.path("executionCount").asInt()).isZero();
    assertThat(rejected.path("errorCode").asText()).isEqualTo("INVALID_ARGUMENT");
    JsonNode succeeded = entries.get(4);
    assertThat(succeeded.path("executionCount").asInt()).isEqualTo(2);
    assertThat(succeeded.path("modelCallSequence").asInt())
        .isEqualTo(entries.get(3).path("sequenceNo").asInt());
    assertThat(succeeded.path("status").asText()).isEqualTo("SUCCESS");
    assertThat(entries.toString()).doesNotContain("不能写入监控表", "已查到商品。", "arguments");
  }

  private JsonNode executionEntries(long messageId, int size) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode execution = get("/api/agent/messages/" + messageId + "/execution", 200);
      assertThat(execution.path("turnId").asLong()).isEqualTo(messageId);
      JsonNode entries = execution.path("entries");
      if (entries.size() == size) {
        return entries;
      }
      Thread.sleep(25);
    }
    return fail("未找到预期执行记录，回答 %d，预期 %d 条", messageId, size);
  }

  @Test
  void resetClearsReconnectedDraftAndAnswerCompletesWithoutSubscribers() throws Exception {
    Sinks.Many<ChatResponse> firstModelStep = model.prepare();
    Sinks.Many<ChatResponse> secondModelStep = model.prepare();
    long messageId = submit(UUID.randomUUID().toString(), "查询订单", 202).path("messageId").asLong();
    try (BufferedReader first = stream(messageId)) {
      event(first, "snapshot");
      emit(firstModelStep, "即将撤回的临时文字");
      event(first, "delta");
      AssistantMessage toolCall = AssistantMessage.builder().toolCalls(List.of(
          new AssistantMessage.ToolCall("order-query-test", "function", "order_query", "{\"limit\":1}")))
          .build();
      assertThat(firstModelStep.tryEmitNext(new ChatResponse(List.of(new Generation(toolCall)))))
          .isEqualTo(Sinks.EmitResult.OK);
      assertThat(firstModelStep.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
      event(first, "reset");
    }
    try (BufferedReader reconnected = stream(messageId)) {
      assertThat(event(reconnected, "snapshot").path("content").asText()).isEmpty();
    }
    emit(secondModelStep, "已查询订单。");
    assertThat(secondModelStep.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode saved = get("/api/agent/messages/" + messageId, 200);
      if ("COMPLETED".equals(saved.path("status").asText())) {
        assertThat(saved.path("content").asText()).isEqualTo("已查询订单。");
        return;
      }
      Thread.sleep(25);
    }
    fail("断开全部连接后回答未完成");
  }

  private BufferedReader reconnectWithDraft(long messageId, String draft) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      BufferedReader reader = stream(messageId);
      if (draft.equals(event(reader, "snapshot").path("content").asText())) {
        return reader;
      }
      reader.close();
      Thread.sleep(25);
    }
    return fail("重连未收到草稿 %s", draft);
  }

  private JsonNode submit(String requestId, String question, int status) throws Exception {
    return sendJson(request("/api/agent/chat/turns").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
            Map.of("clientRequestId", requestId, "message", question)))).build(), status);
  }

  private JsonNode get(String path, int status) throws Exception {
    return sendJson(request(path).GET().build(), status);
  }

  private HttpResponse<String> managementGet(String path, int status) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(
        URI.create("http://127.0.0.1:" + managementPort + path))
        .timeout(Duration.ofSeconds(5)).GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path).isEqualTo(status);
    return response;
  }

  private JsonNode sendJson(HttpRequest request, int status) throws Exception {
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    return json.readTree(response.body());
  }

  private BufferedReader stream(long messageId) throws Exception {
    HttpResponse<java.io.InputStream> response = client.send(
        request("/api/agent/messages/" + messageId + "/stream")
            .header("Accept", "text/event-stream").GET().build(),
        HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    return new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
  }

  private JsonNode event(BufferedReader reader, String expected) throws Exception {
    String name = "";
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith("event:")) {
        name = line.substring("event:".length()).trim();
      } else if (line.startsWith("data:") && expected.equals(name)) {
        return json.readTree(line.substring("data:".length()));
      }
    }
    return fail("流已结束，未收到事件 %s", expected);
  }

  private static void emit(Sinks.Many<ChatResponse> outputs, String text) {
    assertThat(outputs.tryEmitNext(new ChatResponse(
        java.util.List.of(new Generation(new AssistantMessage(text)))))).isEqualTo(Sinks.EmitResult.OK);
  }

  private static void tool(Sinks.Many<ChatResponse> outputs, String name, String arguments) {
    var request = new AssistantMessage.ToolCall("call-" + name, "function", name, arguments);
    var message = AssistantMessage.builder().toolCalls(List.of(request)).build();
    assertThat(outputs.tryEmitNext(new ChatResponse(List.of(new Generation(message)))))
        .isEqualTo(Sinks.EmitResult.OK);
    assertThat(outputs.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    ControlledModel controlledModel() {
      return new ControlledModel();
    }
  }

  /** 可暂停的流式模型替身，用来在 HTTP 断开前后分别发送片段。 */
  static final class ControlledModel implements ChatModel {
    private final AtomicInteger calls = new AtomicInteger();
    private final ConcurrentLinkedQueue<Sinks.Many<ChatResponse>> steps = new ConcurrentLinkedQueue<>();

    Sinks.Many<ChatResponse> prepare() {
      Sinks.Many<ChatResponse> outputs = Sinks.many().unicast().onBackpressureBuffer();
      steps.add(outputs);
      return outputs;
    }

    @Override
    public ToolCallingChatOptions getOptions() {
      return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      throw new UnsupportedOperationException("测试只使用流式模型");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      calls.incrementAndGet();
      Sinks.Many<ChatResponse> outputs = steps.poll();
      return outputs == null
          ? Flux.error(new IllegalStateException("意外的模型调用"))
          : outputs.asFlux();
    }
  }
}
