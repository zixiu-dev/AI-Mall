package com.bitmall.agent.agent.service.model;

import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.runtime.SpringAiStreamReader;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 处理一次模型推理的路由、熔断与指标。输入快照与工具回执由 Agent 主循环持有。 */
@Slf4j
@Service
public class ModelGateway {
  private static final int MODEL_TIMEOUT_SECONDS = 60;

  /** 固定顺序先主模型，再尝试已启用的百炼备用。 */
  private final List<Candidate> candidates;

  /** 只用配置级别的候选作指标标签，避免请求编号产生大量时间序列。 */
  private final MeterRegistry metrics;

  public ModelGateway(
      SpringAiStreamReader primary,
      @Qualifier("bailianChatClient") Optional<ChatClient> bailian,
      MeterRegistry metrics,
      @Value("${app.agent.model.failure-threshold}") int failureThreshold,
      @Value("${app.agent.model.cooldown}") Duration cooldown) {
    this.metrics = metrics;
    Candidate main = candidate("primary", primary, failureThreshold, cooldown);
    this.candidates = bailian.map(client -> List.of(main,
        candidate("bailian", new SpringAiStreamReader(client), failureThreshold, cooldown)))
        .orElseGet(() -> List.of(main));
  }

  /** 各候选最多一次，复用同一组历史和工具定义；失败的草稿在输出下个候选前清空。 */
  public ChatResponse generate(
      List<Message> messages,
      List<ToolCallback> tools,
      AgentExecution execution,
      Consumer<String> text,
      Runnable resetDraft) {
    for (Candidate candidate : candidates) {
      execution.check();
      ModelCircuitBreaker.Permit permit = candidate.breaker.acquire();
      if (permit == null) {
        metrics.counter("agent.model.skipped", "candidate", candidate.name).increment();
        continue;
      }
      Attempt attempt = new Attempt(candidate, text);
      try {
        ChatResponse response = execution.stage("模型推理", MODEL_TIMEOUT_SECONDS,
            () -> candidate.reader.read(messages, tools, execution, attempt::onText));
        attempt.succeed(response);
        return response;
      } catch (CancellationException cancelled) {
        throw cancelled;
      } catch (RuntimeException failure) {
        // 整轮超时和取消属于请求生命周期，不能污染共享模型健康状态。
        execution.check();
        boolean retryable = isUpstreamFailure(failure);
        attempt.outcome = retryable ? "upstream_failure" : "rejected";
        attempt.health = retryable ? ModelCircuitBreaker.Outcome.FAILURE : ModelCircuitBreaker.Outcome.NEUTRAL;
        log.warn("模型调用失败 candidate={} category={} type={}",
            candidate.name, attempt.outcome, failure.getClass().getSimpleName());
        if (attempt.emittedText) {
          resetDraft.run();
        }
        if (!retryable) {
          throw new ModelUnavailableException();
        }
      } finally {
        candidate.breaker.complete(permit, attempt.health);
        metrics.timer("agent.model.attempt", "candidate", candidate.name, "outcome", attempt.outcome)
            .record(System.nanoTime() - attempt.started, TimeUnit.NANOSECONDS);
        attempt.recordUsage();
      }
    }
    throw new ModelUnavailableException();
  }

  /** 只用结构化异常识别上游故障。输入、鉴权和模型策略错误直接结束当前步骤。 */
  private static boolean isUpstreamFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof OpenAIServiceException service) {
        int status = service.statusCode();
        return status == 408 || status == 429 || (status >= 500 && status < 600);
      }
      if (current instanceof AgentExecution.StageTimeoutException
          || current instanceof SpringAiStreamReader.IncompleteResponseException
          || current instanceof OpenAIIoException
          || current instanceof IOException
          || current instanceof TimeoutException) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }

  private Candidate candidate(String name, SpringAiStreamReader reader, int threshold, Duration cooldown) {
    var breaker = new ModelCircuitBreaker(threshold, cooldown, System::nanoTime);
    metrics.gauge("agent.model.circuit.state", List.of(io.micrometer.core.instrument.Tag.of("candidate", name)),
        breaker, value -> value.state().ordinal());
    return new Candidate(name, reader, breaker);
  }

  /** 一个已配置的模型客户端及其独立的熔断状态。 */
  private static final class Candidate {
    private final String name;
    private final SpringAiStreamReader reader;
    private final ModelCircuitBreaker breaker;

    private Candidate(String name, SpringAiStreamReader reader, ModelCircuitBreaker breaker) {
      this.name = name;
      this.reader = reader;
      this.breaker = breaker;
    }
  }

  /** 单次模型尝试的耗时、首段文字和用量，读取流的同一线程负责更新。 */
  private final class Attempt {
    private final Candidate candidate;
    private final Consumer<String> text;
    private final long started = System.nanoTime();
    private String outcome = "interrupted";
    private ModelCircuitBreaker.Outcome health = ModelCircuitBreaker.Outcome.NEUTRAL;
    private boolean emittedText;
    private ChatResponse response;

    private Attempt(Candidate candidate, Consumer<String> text) {
      this.candidate = candidate;
      this.text = text;
    }

    private void onText(String delta) {
      if (delta.isEmpty()) {
        return;
      }
      if (!emittedText) {
        metrics.timer("agent.model.first.text", "candidate", candidate.name)
            .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        emittedText = true;
      }
      text.accept(delta);
    }

    private void succeed(ChatResponse response) {
      this.response = response;
      outcome = "success";
      health = ModelCircuitBreaker.Outcome.SUCCESS;
    }

    private void recordUsage() {
      var usage = response == null ? null : response.getMetadata().getUsage();
      // SDK 以全零提供缺失用量的占位；不把缺失统计当成零成本。
      if (usage == null || usage.getTotalTokens() == null || usage.getTotalTokens() <= 0) {
        metrics.counter("agent.model.usage.missing", "candidate", candidate.name).increment();
        return;
      }
      metrics.counter("agent.model.tokens", "candidate", candidate.name, "kind", "input")
          .increment(usage.getPromptTokens());
      metrics.counter("agent.model.tokens", "candidate", candidate.name, "kind", "output")
          .increment(usage.getCompletionTokens());
    }
  }
}
