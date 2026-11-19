package com.bitmall.agent.order.monitor;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只读采样异步购买及库存整理，使用同一份快照发布指标和管理端聚合信息。 */
@Component
public final class OrderBuildBacklogMonitor {
  private static final List<String> STAGES =
      List.of("processing", "confirm_pending", "release_pending");
  private static final long FRESHNESS_SECONDS = 30;

  private final OrderTraceRepository traces;
  private volatile Snapshot snapshot = new Snapshot(0, Map.of());

  public OrderBuildBacklogMonitor(OrderTraceRepository traces, MeterRegistry registry) {
    this.traces = traces;
    for (String stage : STAGES) {
      Gauge.builder("mall.order.build.pending", this, monitor -> monitor.measure(stage, "pending"))
          .tag("stage", stage)
          .register(registry);
      Gauge.builder(
              "mall.order.build.oldest.wait.seconds",
              this,
              monitor -> monitor.measure(stage, "oldestWaitSeconds"))
          .tag("stage", stage)
          .register(registry);
    }
    Gauge.builder(
            "mall.order.build.sample.timestamp.seconds", this, monitor -> monitor.snapshot.sampledAt)
        .register(registry);
  }

  /** 故障时保留上次快照，最后成功时间继续变旧，告警规则可据此识别失效数据。 */
  @Scheduled(fixedDelay = 10000)
  public void sample() {
    try {
      var rows = traces.asyncBuildBacklog();
      LocalDateTime now = LocalDateTime.now();
      Map<String, Map<String, Long>> stages = new LinkedHashMap<>();
      for (var row : rows) {
        var oldest = RowValues.timestamp(row, "oldest");
        long age = oldest == null
            ? 0
            : Math.max(0, Duration.between(oldest.toLocalDateTime(), now).getSeconds());
        stages.put(
            RowValues.string(row, "stage"),
            Map.of("pending", RowValues.longValue(row, "pending"), "oldestWaitSeconds", age));
      }
      snapshot = new Snapshot(Instant.now().getEpochSecond(), Map.copyOf(stages));
    } catch (RuntimeException unavailable) {
      // 将上次成功时间留给监控判断，数据库异常期间也能查看最近一次结果。
    }
  }

  public Map<String, Object> status() {
    Snapshot current = snapshot;
    return Map.of(
        "sampledAtEpochSeconds", current.sampledAt,
        "fresh", current.sampledAt > 0
            && Instant.now().getEpochSecond() - current.sampledAt < FRESHNESS_SECONDS,
        "stages", current.stages);
  }

  private double measure(String stage, String field) {
    var values = snapshot.stages.get(stage);
    return values == null ? Double.NaN : values.get(field).doubleValue();
  }

  /** 每轮采样的时间与分组一起发布，避免读到半轮更新。 */
  private static final class Snapshot {
    private final long sampledAt;
    private final Map<String, Map<String, Long>> stages;

    private Snapshot(long sampledAt, Map<String, Map<String, Long>> stages) {
      this.sampledAt = sampledAt;
      this.stages = stages;
    }
  }
}
