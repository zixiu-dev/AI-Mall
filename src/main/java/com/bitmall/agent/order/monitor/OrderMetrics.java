package com.bitmall.agent.order.monitor;

import com.bitmall.agent.common.monitor.OneSecondThroughput;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 下单监控与单实例并发保护。
 *
 * <p>指标只用于观测，不参与库存正确性判断。标签只使用模式、事件等有限取值，不为每个 trace 创建独立指标，避免指标基数爆炸。
 */
@Component
public class OrderMetrics {
  private static final String MODE_SYNC = "SYNC";
  private static final String SUBMISSION_MODE_AUTO = "AUTO";
  private static final String EVENT_CREATED = "created";

  /** 注册下单请求数、拒绝数、受理/建单数和耗时指标。 */
  private final MeterRegistry registry;

  /** 记录请求数、同步建单耗时和建单数，供自动路由计算压力。 */
  private final OrderPressureWindow pressure;

  /** 按实际发生的一秒记录响应、受理和建单，统计峰值 QPS 与 TPS；可为 null（未启用时）。 */
  private final OneSecondThroughput seconds;

  /** 当前正在执行的提交请求数。 */
  private final AtomicInteger inFlight = new AtomicInteger();

  /** 单实例入口限额，防止数据库或 Redis 故障时请求无限堆积。 */
  private final Semaphore permits;

  /** 读取单实例并发上限（创建并发许可），以及监控快照里展示的延迟目标和提交模式。 */
  private final OrderProcessingProperties properties;

  /** 不带秒级吞吐统计的构造器，供测试等场景使用。构造时只注册 Gauge，不创建外部监控连接。 */
  public OrderMetrics(
      MeterRegistry registry, OrderProcessingProperties properties, OrderPressureWindow pressure) {
    this(registry, properties, pressure, null);
  }

  @Autowired
  public OrderMetrics(
      MeterRegistry registry,
      OrderProcessingProperties properties,
      OrderPressureWindow pressure,
      OneSecondThroughput seconds) {
    this.seconds = seconds;
    this.pressure = pressure;
    this.registry = registry;
    this.properties = properties;
    permits = new Semaphore(properties.getMaxConcurrent());
    Gauge.builder("mall.order.inflight", inFlight, AtomicInteger::get).register(registry);
    Gauge.builder("mall.order.capacity", properties, OrderProcessingProperties::getMaxConcurrent)
        .register(registry);
  }

  /**
   * 记录从受理到真正创建订单的耗时：包含异步排队时间，不包含用户停留在确认页面的时间。
   *
   * <p>在事务中调用时，等事务提交后才记录，回滚的建单不计入完成耗时。
   */
  public void completed(String mode, LocalDateTime acceptedAt) {
    if (acceptedAt == null) {
      return;
    }
    long nanos = Math.max(0, Duration.between(acceptedAt, LocalDateTime.now()).toNanos());
    runAfterCommitOrNow(
        () ->
            Timer.builder("mall.order.completion.duration")
                .tag("mode", mode)
                .publishPercentileHistogram()
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS));
  }

  /** 进入受理流程，超出本机并发上限时直接返回 429；返回值是开始时间，需原样传给 {@link #exit}。 */
  public long enter() {
    pressure.request();
    registry.counter("mall.order.requests").increment();
    if (!permits.tryAcquire()) {
      registry.counter("mall.order.rejected", "reason", "capacity").increment();
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "下单服务繁忙，请稍后使用原 traceId 重试");
    }
    inFlight.incrementAndGet();
    return System.nanoTime();
  }

  /** 当前正在执行的提交请求数，供路由压力采样使用。 */
  public int inFlight() {
    return inFlight.get();
  }

  /**
   * 与 {@link #enter} 配对，由调用方在 finally 中调用：记录本次请求耗时并归还并发配额。
   *
   * <p>只有同步请求的耗时进入压力窗口，作为 AUTO 路由判断同步链路 P95 的依据。
   */
  public void exit(long started, String mode) {
    long elapsedNanos = System.nanoTime() - started;
    if (MODE_SYNC.equals(mode)) {
      pressure.syncCompleted(elapsedNanos);
    }
    Timer.builder("mall.order.submit.duration")
        .tag("mode", mode)
        .publishPercentileHistogram()
        .register(registry)
        .record(elapsedNanos, TimeUnit.NANOSECONDS);
    inFlight.decrementAndGet();
    permits.release();
  }

  /**
   * 记录受理（accepted）、建单（created）、失败（failed）等状态转换。
   *
   * <p>在事务中调用时，等事务真正提交后才记账：例如 Agent 确认执行的外层事务回滚，不会被误记为建单成功。
   */
  public void committed(String event, String mode) {
    runAfterCommitOrNow(
        () -> {
          registry.counter("mall.order.transitions", "event", event, "mode", mode).increment();
          if (EVENT_CREATED.equals(event)) {
            pressure.orderCreated();
          }
          if (seconds != null) {
            seconds.orderTransition(event, mode);
          }
        });
  }

  /** 有事务同步时延迟到提交成功后执行（回滚则不执行）；不在事务中时立即执行。 */
  private static void runAfterCommitOrNow(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  /** 记录重复提交、重复消费等有限取值的事件。 */
  public void event(String event) {
    registry.counter("mall.order.events", "event", event).increment();
  }

  /** 本机指标快照，用于本地调试；多实例告警应汇总 Prometheus 指标。 */
  public Map<String, Object> snapshot() {
    Map<String, Object> result = new LinkedHashMap<>(pressure.snapshot());
    result.put("inFlight", inFlight.get());
    result.put("maxConcurrent", properties.getMaxConcurrent());
    result.put("latencyTargetMs", properties.getLatencyTargetMs());
    result.put("autoModeSwitch", SUBMISSION_MODE_AUTO.equals(properties.getSubmissionMode()));
    result.put("submitRequestsTotal", sumCounters("mall.order.requests"));
    result.put("ordersCreatedTotal", transitionTotal(EVENT_CREATED));
    result.put("asyncAcceptedTotal", transitionTotal("accepted"));
    result.put("failedTotal", transitionTotal("failed"));
    // 压测用两次快照的累计值之差除以实际间隔计算 TPS；这里给出订单数而不是 HTTP 受理数，避免拿受理数冒充订单数。
    result.put("sampledAtEpochMs", System.currentTimeMillis());
    return result;
  }

  private double sumCounters(String name) {
    return registry.find(name).counters().stream().mapToDouble(Counter::count).sum();
  }

  /** 某个事件在所有模式下的累计次数。 */
  private double transitionTotal(String event) {
    return registry.find("mall.order.transitions").tag("event", event).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }
}
