package com.bitmall.agent.order.monitor;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import io.micrometer.core.instrument.*;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

/** 下单监控与单实例并发保护。指标不参与库存正确性；只使用模式、阶段等有限标签，不为每个 trace 创建指标。 */
@Component
public class OrderMetrics {
  /** 受理到真正创建订单的时间，包含排队，不包含用户停留在确认页面的时间。 */
  public void completed(String mode, java.time.LocalDateTime acceptedAt) {
    if (acceptedAt == null) return;
    long nanos =
        Math.max(
            0, java.time.Duration.between(acceptedAt, java.time.LocalDateTime.now()).toNanos());
    Runnable record =
        () ->
            Timer.builder("mall.order.completion.duration")
                .tag("mode", mode)
                .publishPercentileHistogram()
                .register(registry)
                .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            /** 回滚事务不计入完成耗时。 */
            @Override
            public void afterCommit() {
              record.run();
            }
          });
    else record.run();
  }

  /** Micrometer 注册器，Prometheus 可汇总多个实例。 */
  private final MeterRegistry registry;

  private final OrderPressureWindow pressure;

  /** 可选的一秒明细统计；默认关闭，压测时在事务提交回调中按实际完成秒记账。 */
  private final com.bitmall.agent.common.monitor.OneSecondThroughput seconds;

  /** 当前正在执行的提交请求数。 */
  private final AtomicInteger inFlight = new AtomicInteger();

  /** 单实例入口限额，防止数据库或 Redis 故障时请求无限堆积。 */
  private final Semaphore permits;

  /** 压测后设置的耗时目标，用于监控，自动路由使用独立的滚动窗口。 */
  private final OrderProcessingProperties properties;

  /** 注册当前执行数量，构造时不创建外部监控连接。 */
  public OrderMetrics(
      MeterRegistry registry, OrderProcessingProperties properties, OrderPressureWindow pressure) {
    this(registry, properties, pressure, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrderMetrics(
      MeterRegistry registry,
      OrderProcessingProperties properties,
      OrderPressureWindow pressure,
      com.bitmall.agent.common.monitor.OneSecondThroughput seconds) {
    this.seconds = seconds;
    this.pressure = pressure;
    this.registry = registry;
    this.properties = properties;
    permits = new Semaphore(properties.getMaxConcurrent());
    Gauge.builder("mall.order.inflight", inFlight, AtomicInteger::get).register(registry);
    Gauge.builder("mall.order.capacity", properties, OrderProcessingProperties::getMaxConcurrent)
        .register(registry);
  }

  /** 进入受理流程，超出本机上限时快速拒绝。 */
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

  /** finally 释放并发配额，并记录所有成功或异常请求的耗时。 */
  public int inFlight() {
    return inFlight.get();
  }

  public void exit(long started, String mode) {
    if ("SYNC".equals(mode)) pressure.syncCompleted(System.nanoTime() - started);
    Timer.builder("mall.order.submit.duration")
        .tag("mode", mode)
        .publishPercentileHistogram()
        .register(registry)
        .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
    inFlight.decrementAndGet();
    permits.release();
  }

  /** 事务真正提交后才统计首次受理和最终成功，Agent 外层事务回滚不会被误记为成功。 */
  public void committed(String event, String mode) {
    Runnable add =
        () -> {
          registry.counter("mall.order.transitions", "event", event, "mode", mode).increment();
          if ("created".equals(event)) pressure.orderCreated();
          if (seconds != null) seconds.orderTransition(event, mode);
        };
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            /** 指标只统计已经提交的业务结果。 */
            @Override
            public void afterCommit() {
              add.run();
            }
          });
    else add.run();
  }

  /** 记录重复消费等有限事件标签。 */
  public void event(String event) {
    registry.counter("mall.order.events", "event", event).increment();
  }

  /** 本机快照用于本地调试，多实例告警应汇总 Prometheus 指标。 */
  public Map<String, Object> snapshot() {
    var result = new java.util.LinkedHashMap<String, Object>(pressure.snapshot());
    result.put("inFlight", inFlight.get());
    result.put("maxConcurrent", properties.getMaxConcurrent());
    result.put("latencyTargetMs", properties.getLatencyTargetMs());
    result.put("autoModeSwitch", "AUTO".equals(properties.getSubmissionMode()));
    result.put(
        "submitRequestsTotal",
        registry.find("mall.order.requests").counters().stream().mapToDouble(Counter::count).sum());
    result.put("ordersCreatedTotal", total("created"));
    result.put("asyncAcceptedTotal", total("accepted"));
    result.put("failedTotal", total("failed"));
    // 压测用两次累计值之差除以实际观测时长计算 TPS，不拿 HTTP 受理数冒充订单数。
    result.put("sampledAtEpochMs", System.currentTimeMillis());
    return result;
  }

  private double total(String event) {
    return registry.find("mall.order.transitions").tag("event", event).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }
}
