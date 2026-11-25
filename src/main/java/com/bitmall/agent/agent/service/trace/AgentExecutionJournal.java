package com.bitmall.agent.agent.service.trace;

import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import com.bitmall.agent.agent.dao.repository.AgentExecutionDetailRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/** 有界诊断写入队列。聊天线程只提交已完成的明细，专用线程负责分批写入与清理。 */
@Slf4j
@Component
@DependsOnDatabaseInitialization
public class AgentExecutionJournal {
  private static final int WRITE_BATCH = 128;
  private static final int DELETE_BATCH = 500;

  /** 两张明细表的访问组件。 */
  private final AgentExecutionDetailRepository details;
  /** 记录诊断队列积压、丢弃及清理失败，不使用会话编号作为标签。 */
  private final MeterRegistry metrics;
  /** 最多保留多少天的详情。 */
  private final int retentionDays;
  private final ArrayBlockingQueue<AgentExecutionDetailDO> queue;
  private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(task -> {
    Thread thread = new Thread(task, "agent-execution-journal");
    thread.setDaemon(true);
    return thread;
  });
  private boolean closed;

  public AgentExecutionJournal(AgentExecutionDetailRepository details, MeterRegistry metrics,
      @Value("${app.agent.execution-detail.queue-capacity:4096}") int capacity,
      @Value("${app.agent.execution-detail.retention-days:7}") int retentionDays) {
    if (retentionDays < 1) {
      throw new IllegalArgumentException("Agent 执行明细至少保留一天");
    }
    this.details = details;
    this.metrics = metrics;
    this.retentionDays = retentionDays;
    this.queue = new ArrayBlockingQueue<>(capacity);
    metrics.gauge("agent.execution.detail.queue.size", queue, ArrayBlockingQueue::size);
  }

  @PostConstruct
  void start() {
    writer.scheduleWithFixedDelay(this::flushBatch, 100, 100, TimeUnit.MILLISECONDS);
    writer.scheduleWithFixedDelay(this::prune, 1, 1, TimeUnit.MINUTES);
  }

  /** 一条后台助手消息对应本轮唯一的诊断上下文，调用方显式传给工作线程。 */
  public AgentTurnTrace startTurn(long turnId) {
    return new AgentTurnTrace(turnId, this::offer);
  }

  /** 队列满时不等待、不回退到调用方线程写库。 */
  synchronized void offer(AgentExecutionDetailDO entry) {
    if (closed) {
      drop("shutdown", 1);
    } else if (!queue.offer(entry)) {
      drop("queue_full", 1);
    }
  }

  public List<AgentExecutionDetailDO> find(long turnId) {
    return details.find(turnId, cutoff());
  }

  /** 每张表单独写入，一批失败不阻止另一表的数据保存，也不阻止下一批。 */
  void flushBatch() {
    List<AgentExecutionDetailDO> batch = new ArrayList<>(WRITE_BATCH);
    queue.drainTo(batch, WRITE_BATCH);
    if (batch.isEmpty()) {
      return;
    }
    List<AgentExecutionDetailDO> models = batch.stream().filter(AgentExecutionDetailDO::isModelCall).toList();
    List<AgentExecutionDetailDO> items = batch.stream().filter(row -> !row.isModelCall()).toList();
    persist(models, details::writeModelCalls, "model");
    persist(items, details::writeItems, "item");
  }

  private void persist(List<AgentExecutionDetailDO> rows,
      java.util.function.Consumer<List<AgentExecutionDetailDO>> write, String type) {
    if (rows.isEmpty()) {
      return;
    }
    try {
      write.accept(rows);
    } catch (RuntimeException failure) {
      drop("write_failed", rows.size());
      log.warn("执行明细写入失败 type={} count={} errorType={}",
          type, rows.size(), failure.getClass().getSimpleName());
    }
  }

  void prune() {
    try {
      details.prune(cutoff(), DELETE_BATCH);
    } catch (RuntimeException failure) {
      metrics.counter("agent.execution.detail.prune.failed").increment();
      log.warn("执行明细清理失败 errorType={}", failure.getClass().getSimpleName());
    }
  }

  private LocalDateTime cutoff() {
    return LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
  }

  private void drop(String reason, int count) {
    metrics.counter("agent.execution.detail.dropped", "reason", reason).increment(count);
  }

  @PreDestroy
  synchronized void stop() {
    closed = true;
    writer.shutdownNow();
    List<AgentExecutionDetailDO> remaining = new ArrayList<>();
    queue.drainTo(remaining);
    if (!remaining.isEmpty()) {
      drop("shutdown", remaining.size());
    }
  }
}
