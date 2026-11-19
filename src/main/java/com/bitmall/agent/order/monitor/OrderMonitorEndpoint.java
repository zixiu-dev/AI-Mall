package com.bitmall.agent.order.monitor;

import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import java.util.*;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 本机下单监控快照。业务积压定期采样，不在每次下单时 COUNT 全表；多实例应通过 Prometheus 汇总。 */
@Component
@Endpoint(id = "ordermonitor")
public class OrderMonitorEndpoint {
  /** 最近采样的持久化在途任务数。 */
  private final java.util.concurrent.atomic.AtomicLong pending =
      new java.util.concurrent.atomic.AtomicLong();

  /** 最近采样时最老任务的等待秒数。 */
  private final java.util.concurrent.atomic.AtomicLong oldestAge =
      new java.util.concurrent.atomic.AtomicLong();

  /** 入口计数和并发信息。 */
  private final OrderMetrics metrics;

  private final OrderPressureRouter routing;

  private final OrderBuildBacklogMonitor buildBacklog;

  /** 定期读取持久化积压。 */
  private final OrderTraceRepository traces;

  /** 最近一次业务采样，失败时保留上次结果。 */
  private volatile Map<String, Object> backlog = Map.of();

  /** 注入监控和只读聚合数据源。 */
  public OrderMonitorEndpoint(
      OrderMetrics metrics,
      OrderTraceRepository traces,
      io.micrometer.core.instrument.MeterRegistry registry,
      OrderPressureRouter routing,
      OrderBuildBacklogMonitor buildBacklog) {
    this.routing = routing;
    this.metrics = metrics;
    this.traces = traces;
    this.buildBacklog = buildBacklog;
    io.micrometer.core.instrument.Gauge.builder(
            "mall.order.pending", pending, java.util.concurrent.atomic.AtomicLong::get)
        .register(registry);
    io.micrometer.core.instrument.Gauge.builder(
            "mall.order.oldest.pending.seconds",
            oldestAge,
            java.util.concurrent.atomic.AtomicLong::get)
        .register(registry);
  }

  /** 每三十秒采样一次，监控故障不能阻断订单交易。 */
  @Scheduled(fixedDelay = 30000)
  public void sample() {
    try {
      backlog = Collections.unmodifiableMap(new LinkedHashMap<>(traces.backlog()));
      Object count = backlog.get("pending"), oldest = backlog.get("oldest");
      pending.set(count instanceof Number n ? n.longValue() : 0);
      java.time.LocalDateTime time =
          oldest instanceof java.sql.Timestamp t
              ? t.toLocalDateTime()
              : oldest instanceof java.time.LocalDateTime t ? t : null;
      oldestAge.set(
          time == null
              ? 0
              : Math.max(
                  0, java.time.Duration.between(time, java.time.LocalDateTime.now()).getSeconds()));
    } catch (RuntimeException ignored) {
      /* 数据源恢复后再采样。 */
    }
  }

  /** 管理端口提供只读监控信息，不暴露用户与 trace 明细。 */
  @ReadOperation
  public Map<String, Object> status() {
    var result = new LinkedHashMap<>(metrics.snapshot());
    result.put("backlog", backlog);
    result.put("asyncBuild", buildBacklog.status());
    result.put("routing", routing.status());
    return result;
  }
}
