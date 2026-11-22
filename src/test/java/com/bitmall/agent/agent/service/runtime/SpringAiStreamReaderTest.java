package com.bitmall.agent.agent.service.runtime;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

/** 验证同步读取仍逐段发送，断连和超时会取消上游，并在实际退出时释放会话执行权。 */
class SpringAiStreamReaderTest {
  private SpringAiStreamReader reader(Flux<ChatResponse> source) {
    ChatModel model =
        new ChatModel() {
          @Override
          public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
          }

          @Override
          public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException();
          }

          @Override
          public Flux<ChatResponse> stream(Prompt prompt) {
            return source;
          }
        };
    return new SpringAiStreamReader(ChatClient.builder(model).build());
  }

  @Test
  void sendsTextBeforeModelCompletesAndCancelsSourceOnDisconnect() throws Exception {
    CountDownLatch token = new CountDownLatch(1);
    CountDownLatch disposed = new CountDownLatch(1);
    var reader =
        reader(
            Flux.concat(
                    Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("首段"))))),
                    Flux.<ChatResponse>never())
                .doFinally(signal -> disposed.countDown()));
    var timer = Executors.newSingleThreadScheduledExecutor();
    var worker = Executors.newSingleThreadExecutor();
    AgentRunGate gate = new AgentRunGate();
    try (AgentExecution execution = new AgentExecution(timer, 10)) {
      Future<?> task =
          worker.submit(
              () -> {
                execution.bind();
                try (AgentRunGate.Lease lease = gate.acquire("stream")) {
                  reader.read(
                      List.of(new UserMessage("测试")),
                      List.of(),
                      execution,
                      text -> {
                        assertThat(text).isEqualTo("首段");
                        token.countDown();
                      });
                }
              });
      assertThat(token.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(task.isDone()).isFalse();
      assertThatThrownBy(() -> gate.acquire("stream")).hasMessageContaining("正在处理中");
      execution.cancel();
      assertThatThrownBy(() -> task.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
      assertThat(disposed.await(5, TimeUnit.SECONDS)).isTrue();
      try (var lease = gate.acquire("stream")) {
        assertThat(lease).isNotNull();
      }
    } finally {
      worker.shutdownNow();
      timer.shutdownNow();
    }
  }

  @Test
  void timeoutInterruptsBlockingReadAndStopsUpstream() throws Exception {
    CountDownLatch disposed = new CountDownLatch(1);
    var reader = reader(Flux.<ChatResponse>never().doFinally(signal -> disposed.countDown()));
    var timer = Executors.newSingleThreadScheduledExecutor();
    try (AgentExecution execution = new AgentExecution(timer, 10)) {
      execution.bind();
      assertThatThrownBy(
              () ->
                  execution.stage(
                      "模型推理",
                      1,
                      () ->
                          reader.read(
                              List.of(new UserMessage("测试")), List.of(), execution, text -> {})))
          .hasMessageContaining("模型推理超时");
      assertThat(Thread.currentThread().isInterrupted()).isFalse();
      assertThat(disposed.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      timer.shutdownNow();
    }
  }

  @Test
  void cancellationKeepsGateUntilInterruptIgnoringBusinessActuallyExits() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    var timer = Executors.newSingleThreadScheduledExecutor();
    var worker = Executors.newSingleThreadExecutor();
    AgentRunGate gate = new AgentRunGate();
    try (AgentExecution execution = new AgentExecution(timer, 10)) {
      Future<?> task =
          worker.submit(
              () -> {
                execution.bind();
                try (var lease = gate.acquire("slow-tool")) {
                  execution.stage(
                      "工具执行",
                      5,
                      () -> {
                        started.countDown();
                        boolean done = false;
                        while (!done) {
                          try {
                            finish.await();
                            done = true;
                          } catch (InterruptedException ignored) {
                            /* 模拟数据库调用暂时忽略中断。 */
                          }
                        }
                        return "已处理";
                      });
                  throw new AssertionError("取消后不应进入后续步骤");
                }
              });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      execution.cancel();
      assertThatThrownBy(() -> gate.acquire("slow-tool")).hasMessageContaining("正在处理中");
      finish.countDown();
      assertThatThrownBy(() -> task.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(CancellationException.class);
      try (var lease = gate.acquire("slow-tool")) {
        assertThat(lease).isNotNull();
      }
    } finally {
      finish.countDown();
      worker.shutdownNow();
      timer.shutdownNow();
    }
  }

  @Test
  void cancelledQueuedExecutionNeverStartsAndExpiredBudgetRejectsNextStage() {
    var timer = Executors.newSingleThreadScheduledExecutor();
    try (AgentExecution cancelled = new AgentExecution(timer, 10);
        AgentExecution expired = new AgentExecution(timer, 0)) {
      cancelled.cancel();
      assertThatThrownBy(cancelled::bind).isInstanceOf(CancellationException.class);
      assertThatThrownBy(() -> expired.stage("工具", 1, () -> "不应执行"))
          .hasMessageContaining("总执行时间超限");
    } finally {
      timer.shutdownNow();
    }
  }
}
