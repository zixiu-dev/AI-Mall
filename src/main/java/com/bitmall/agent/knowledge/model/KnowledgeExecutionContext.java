package com.bitmall.agent.knowledge.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/** 仅活跃请求持有的可信知识上下文，不序列化进会话、Redis 或模型工具参数。 */
public final class KnowledgeExecutionContext {
  @Getter
  private final String requestId;
  @Getter
  private final String originalQuestion;
  @Getter
  private final List<String> history;
  @Getter
  private final long deadlineNanos;
  private final AtomicInteger calls = new AtomicInteger();
  @Getter
  private final Map<String, Object> cache = new ConcurrentHashMap<>();

  public KnowledgeExecutionContext(
      String requestId, String originalQuestion, List<String> history, long timeoutMillis) {
    this.requestId = requestId;
    this.originalQuestion = originalQuestion;
    this.history =
        history.stream()
            .skip(Math.max(0, history.size() - 6))
            .map(s -> s.substring(0, Math.min(s.length(), 1000)))
            .toList();
    this.deadlineNanos =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
  }

  /** 防止外层反复调用同一搜索工具无限消耗模型预算，缓存命中也计入调用次数。 */
  public void enter(int maximum) {
    if (calls.incrementAndGet() > maximum || System.nanoTime() >= deadlineNanos) {
      throw new IllegalStateException("本轮知识检索预算已用尽");
    }
  }
}
