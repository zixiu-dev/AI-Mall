package com.bitmall.agent.order.monitor;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.order.config.*;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 自动路由采样器：每秒上报本机压力，Redis 聚合在线实例并协调唯一模式；请求只读有时效的本地快照。 */
@Component
public class OrderPressureRouter {
  private final OrderProcessingProperties processing;
  private final OrderAutoRoutingProperties limits;
  private final OrderMessagingProperties messaging;
  private final InventoryReservationProperties inventory;
  private final OrderPressureWindow window;
  private final OrderMetrics metrics;
  private final OrderTraceRepository traces;
  private final ObjectProvider<RedissonClient> redis;
  private final ObjectMapper json;
  private final DataSource dataSource;
  private final MeterRegistry registry;
  private final String instance = UUID.randomUUID().toString();
  private final String script = RedissonSupport.script("order_auto_route.lua");
  private volatile Map<String, Object> last =
      Map.of("mode", "SYNC", "accepting", false, "reason", "WARMING_UP");
  private volatile long sampledAt;

  public OrderPressureRouter(
      OrderProcessingProperties processing,
      OrderAutoRoutingProperties limits,
      OrderMessagingProperties messaging,
      InventoryReservationProperties inventory,
      OrderPressureWindow window,
      OrderMetrics metrics,
      OrderTraceRepository traces,
      @Qualifier("orderRedisson") ObjectProvider<RedissonClient> redis,
      ObjectMapper json,
      DataSource dataSource,
      MeterRegistry registry) {
    this.processing = processing;
    this.limits = limits;
    this.messaging = messaging;
    this.inventory = inventory;
    this.window = window;
    this.metrics = metrics;
    this.traces = traces;
    this.redis = redis;
    this.json = json;
    this.dataSource = dataSource;
    this.registry = registry;
    io.micrometer.core.instrument.Gauge.builder(
            "mall.order.routing.async", this, r -> "ASYNC".equals(r.last.get("mode")) ? 1 : 0)
        .register(registry);
    io.micrometer.core.instrument.Gauge.builder(
            "mall.order.routing.accepting",
            this,
            r -> r.fresh() && Boolean.TRUE.equals(r.last.get("accepting")) ? 1 : 0)
        .register(registry);
  }

  public boolean automatic() {
    return "AUTO".equals(processing.getSubmissionMode());
  }

  private boolean fresh() {
    return sampledAt != 0 && System.nanoTime() - sampledAt < limits.getStaleAfter().toNanos();
  }

  /** 发号/提议阶段只是提示；真正首次预扣前再次选择，随后模式永久固定。 */
  public String hint() {
    return String.valueOf(last.get("mode"));
  }

  public String select() {
    if (!fresh() || !Boolean.TRUE.equals(last.get("accepting")))
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "下单服务繁忙，请稍后使用原购买编号重试");
    return hint();
  }

  /** SQL 积压是后台采样，绝不在每个请求里 COUNT；异常不能被转换成零压力。 */
  @Scheduled(
      fixedDelayString = "${app.order-routing.sample-interval-ms:1000}",
      initialDelayString = "${app.order-routing.initial-delay-ms:500}")
  public void sample() {
    if (!automatic()) return;
    try {
      if (!inventory.isEnabled()) throw new IllegalStateException("AUTO 必须启用共享库存预扣，不能让同步绕过异步预占");
      Map<String, Object> sample = new LinkedHashMap<>(window.snapshot());
      sample.put("inflight", metrics.inFlight());
      sample.put("permits", processing.getMaxConcurrent());
      sample.put("capacity", limits.getSyncQpsPerInstance());
      double usage = 0;
      int waiting = 0;
      if (dataSource.isWrapperFor(HikariDataSource.class)) {
        var pool = dataSource.unwrap(HikariDataSource.class);
        var mx = pool.getHikariPoolMXBean();
        if (mx != null) {
          usage = mx.getActiveConnections() / (double) Math.max(1, pool.getMaximumPoolSize());
          waiting = mx.getThreadsAwaitingConnection();
        }
      }
      sample.put("databaseUsage", usage);
      sample.put("databaseWaiters", waiting);
      var backlog = traces.backlog();
      sample.put("pending", ((Number) backlog.getOrDefault("pending", 0)).longValue());
      Object oldest = backlog.get("oldest");
      LocalDateTime time =
          oldest instanceof java.sql.Timestamp t
              ? t.toLocalDateTime()
              : oldest instanceof LocalDateTime t ? t : null;
      sample.put(
          "oldestSeconds",
          time == null ? 0 : Math.max(0, Duration.between(time, LocalDateTime.now()).getSeconds()));
      sample.put("healthy", true);
      sample.put("asyncReady", messaging.isEnabled());
      Map<String, Object> config = configuration();
      String prefix = processing.getNamespace() + "{routing}:";
      String value =
          RedissonSupport.eval(
              redis.getObject(),
              script,
              RScript.ReturnType.VALUE,
              List.of(prefix + "nodes", prefix + "samples", prefix + "state"),
              instance,
              json.writeValueAsString(sample),
              json.writeValueAsString(config));
      if (value == null) throw new IllegalStateException("缺少路由采样结果");
      last =
          Collections.unmodifiableMap(
              json.readValue(value, new TypeReference<Map<String, Object>>() {}));
      sampledAt = System.nanoTime();
      if (Boolean.TRUE.equals(last.get("transition"))) {
        registry.counter("mall.order.routing.transitions", "mode", hint()).increment();
        org.slf4j.LoggerFactory.getLogger(getClass())
            .info(
                "自动下单模式切换：mode={},qps={},p95={}ms,dbUsage={}",
                hint(),
                last.get("qps"),
                last.get("syncP95Ms"),
                last.get("databaseUsage"));
      }
    } catch (Exception ex) {
      if (!"SAMPLING_UNAVAILABLE".equals(last.get("reason")))
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("下单压力采样不可用，暂停新的受理：{}", ex.getClass().getSimpleName());
      last = Map.of("mode", hint(), "accepting", false, "reason", "SAMPLING_UNAVAILABLE");
      registry.counter("mall.order.routing.errors").increment();
      // 尽量通知其他实例本机采样失败，不能让旧的低压力值继续参与正常决策。
      try {
        String prefix = processing.getNamespace() + "{routing}:";
        var failed =
            Map.of(
                "qps",
                0,
                "capacity",
                limits.getSyncQpsPerInstance(),
                "inflight",
                0,
                "permits",
                processing.getMaxConcurrent(),
                "syncSamples",
                0,
                "syncP95Ms",
                0,
                "databaseUsage",
                0,
                "databaseWaiters",
                0,
                "pending",
                0,
                "oldestSeconds",
                0);
        var unhealthy = new LinkedHashMap<String, Object>(failed);
        unhealthy.put("healthy", false);
        unhealthy.put("asyncReady", false);
        RedissonSupport.eval(
            redis.getObject(),
            script,
            RScript.ReturnType.VALUE,
            List.of(prefix + "nodes", prefix + "samples", prefix + "state"),
            instance,
            json.writeValueAsString(unhealthy),
            json.writeValueAsString(configuration()));
      } catch (Exception ignored) {
        /* 协调 Redis 也故障时，各实例的本地采样会在 stale-after 后停止接单。 */
      }
    }
  }

  private Map<String, Object> configuration() {
    var config = new LinkedHashMap<String, Object>();
    config.put("staleMs", limits.getStaleAfter().toMillis());
    config.put("cooldownMs", limits.getCooldown().toMillis());
    config.put("highSamples", limits.getHighSamples());
    config.put("lowSamples", limits.getLowSamples());
    config.put("minimumSyncSamples", limits.getMinimumSyncSamples());
    config.put("p95High", limits.getSyncP95HighMs());
    config.put("p95Low", limits.getSyncP95LowMs());
    config.put("inFlightHigh", limits.getInFlightHigh());
    config.put("inFlightLow", limits.getInFlightLow());
    config.put("databaseHigh", limits.getDatabaseHigh());
    config.put("databaseLow", limits.getDatabaseLow());
    config.put("maxPending", limits.getMaxPending());
    config.put("recoverPending", limits.getRecoverPending());
    config.put("maxQueueAge", limits.getMaxQueueAgeSeconds());
    return config;
  }

  public Map<String, Object> status() {
    if (!automatic())
      return Map.of(
          "automatic",
          false,
          "mode",
          processing.getSubmissionMode(),
          "configuredMode",
          processing.getSubmissionMode(),
          "accepting",
          true,
          "reason",
          "MANUAL_MODE",
          "fresh",
          true,
          "instance",
          instance);
    var result = new LinkedHashMap<String, Object>(last);
    result.put("automatic", automatic());
    result.put("fresh", fresh());
    result.put("configuredMode", processing.getSubmissionMode());
    result.put("instance", instance);
    return result;
  }
}
