package com.bitmall.agent.agent.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.bitmall.agent.agent.config.SpringAiConfiguration;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/** 可选的百炼真实联调。主模型模拟网络失败，备用只接收合成样本，不接触商城业务数据。 */
@EnabledIfEnvironmentVariable(named = "AGENT_BAILIAN_LIVE_TEST", matches = "true")
class RealBailianGatewayIntegrationTest {
  @Test
  @Timeout(60)
  void fallbackStreamsTextAndToolRoundAndReportsUsage() {
    String key = System.getenv("AGENT_BAILIAN_API_KEY");
    String base = System.getenv("AGENT_BAILIAN_BASE_URL");
    String model = System.getenv().getOrDefault("AGENT_BAILIAN_MODEL", "qwen-plus");
    var backup = new SpringAiConfiguration().bailianChatClient(key, base, model);
    var primaryCalls = new AtomicInteger();
    var primary = ChatClient.builder(new FailingModel(primaryCalls)).build();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    var metrics = new SimpleMeterRegistry();
    try {
      var gateway = new ModelGateway(new SpringAiStreamReader(primary), Optional.of(backup),
          metrics, 2, Duration.ofSeconds(30), "local-main", model);
      var deltas = new ArrayList<String>();
      try (var execution = new AgentExecution(scheduler, 50)) {
        execution.bind();
        var plain = gateway.generate(List.of(new UserMessage("请简短回复：百炼联调正常。")),
            List.of(), execution, deltas::add, deltas::clear);
        assertThat(plain.getResult().getOutput().getText()).isNotBlank();
        assertThat(deltas).isNotEmpty();

        var toolExecutions = new AtomicInteger();
        var tool = FunctionToolCallback.builder("lookup_test_stock", (String ignored) -> {
              toolExecutions.incrementAndGet();
              return "不应由模型客户端自动执行";
            })
            .description("查询指定合成 SKU 的测试库存")
            .inputType(String.class)
            .inputSchema("""
                {"type":"object","properties":{"sku":{"type":"string"}},"required":["sku"]}
                """)
            .build();
        var history = new ArrayList<Message>();
        history.add(new UserMessage("请先调用 lookup_test_stock，sku 填 TEST-001，再报告返回的件数。"));
        var toolAnswer = gateway.generate(history, List.of(tool), execution, ignored -> {}, () -> {})
            .getResult().getOutput();
        var call = toolAnswer.getToolCalls().get(0);
        assertThat(call.name()).isEqualTo("lookup_test_stock");
        assertThat(new ObjectMapper().readTree(call.arguments()).path("sku").asText()).isEqualTo("TEST-001");
        assertThat(toolExecutions.get()).isZero();
        history.add(toolAnswer);
        history.add(ToolResponseMessage.builder().responses(List.of(
            new ToolResponseMessage.ToolResponse(call.id(), call.name(), "{\"stock\":17}"))).build());
        var completed = gateway.generate(history, List.of(tool), execution, ignored -> {}, () -> {});
        assertThat(completed.getResult().getOutput().getText()).contains("17");
      }

      assertThat(primaryCalls.get()).isEqualTo(2);
      assertThat(metrics.get("agent.model.skipped").tag("candidate", "primary").counter().count()).isEqualTo(1);
      var inputTokens = metrics.get("agent.model.tokens").tags("candidate", "bailian", "kind", "input")
          .counter().count();
      var outputTokens = metrics.get("agent.model.tokens").tags("candidate", "bailian", "kind", "output")
          .counter().count();
      assertThat(inputTokens).isPositive();
      assertThat(outputTokens).isPositive();
      assertThat(metrics.find("agent.model.usage.missing").tag("candidate", "bailian").counter()).isNull();
      System.out.printf("BAILIAN_LIVE_OK attempts=3 input_tokens=%.0f output_tokens=%.0f%n", inputTokens, outputTokens);
    } finally {
      metrics.close();
      scheduler.shutdownNow();
    }
  }

  /** 给当前模型步骤注入连接失败，验证网关的真实切换逻辑。 */
  private static final class FailingModel implements ChatModel {
    private final AtomicInteger calls;

    private FailingModel(AtomicInteger calls) {
      this.calls = calls;
    }

    @Override
    public ToolCallingChatOptions getOptions() {
      return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      throw new UnsupportedOperationException("测试只使用流式接口");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      calls.incrementAndGet();
      return Flux.error(new IOException("模拟主模型连接失败"));
    }
  }
}
