package com.bitmall.agent.order.monitor;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.config.OrderAutoRoutingProperties;
import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.mq.BrokerHealthChecker;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AUTO 模式下的同步/异步路由器。
 *
 * <p>工作方式：
 *
 * <ol>
 *   <li>每个实例由 {@link #sample()} 定时（默认每秒）采集本机压力：QPS、并发数、数据库连接池、异步积压、MQ 是否可用。
 *   <li>样本通过 order_auto_route.lua 写入 Redis，脚本汇总所有在线实例，统一决定全集群当前是 SYNC 还是 ASYNC、是否接单，
 *       保证多个实例不会各自选出不同模式。
 *   <li>脚本返回的决策保存为本地快照 {@code latestSnapshot}。下单请求只读这个快照，不访问 Redis；快照超过 stale-after
 *       未刷新就视为失效，拒绝新的受理。
 * </ol>
 */
@Component
public class OrderPressureRouter {
  private static final Logger log = LoggerFactory.getLogger(OrderPressureRouter.class);

  private static final String SUBMISSION_MODE_AUTO = "AUTO";
  private static final String MODE_SYNC = "SYNC";
  private static final String MODE_ASYNC = "ASYNC";

  /** 路由快照中被本类直接读取的键；其余键（qps、nodes 等）由 Lua 脚本给出，只透传给 status()。 */
  private static final String KEY_MODE = "mode";
  private static final String KEY_ACCEPTING = "accepting";
  private static final String KEY_REASON = "reason";
  private static final String KEY_TRANSITION = "transition";

  private static final String REASON_WARMING_UP = "WARMING_UP";
  private static final String REASON_SAMPLING_UNAVAILABLE = "SAMPLING_UNAVAILABLE";
  private static final String REASON_MANUAL_MODE = "MANUAL_MODE";
  private static final String REASON_ASYNC_UNAVAILABLE = "ASYNC_UNAVAILABLE";

  /** 读取提交模式、单实例并发上限和 Redis key 命名空间。 */
  private final OrderProcessingProperties processing;

  /** 读取压力路由阈值和升降级滞回配置，减少模式频繁切换。 */
  private final OrderAutoRoutingProperties limits;

  /** 读取订单消息是否启用；未启用时异步链路不可用。 */
  private final OrderMessagingProperties messaging;

  /** 自动路由要求启用 Redis 库存预占，采样时检查这个开关。 */
  private final InventoryReservationProperties inventory;

  /** 读取最近时间窗口内的 QPS 和同步建单耗时。 */
  private final OrderPressureWindow window;

  /** 读取本机在途下单数，作为压力样本。 */
  private final OrderMetrics metrics;

  /** 读取异步建单积压，作为压力样本。 */
  private final OrderTraceRepository traces;

  /** 按需取得交易专用的 Redisson 客户端，首次实际使用时才建立连接。 */
  private final ObjectProvider<RedissonClient> redis;

  /** 按需读取 Broker 发送探针的健康状态，核对异步链路是否能够接单。 */
  private final ObjectProvider<BrokerHealthChecker> brokerHealthChecker;

  /** 把本机压力样本序列化后上报 Redis，并解析路由脚本返回的决策。 */
  private final ObjectMapper json;

  /** 取得业务数据库连接，供事务执行或数据库连接池压力采样。 */
  private final DataSource dataSource;

  /** 注册路由模式、受理状态指标，统计模式切换次数。 */
  private final MeterRegistry registry;

  /** 本实例在 Redis 协调表中的唯一标识，进程重启后会换一个新值。 */
  private final String instance = UUID.randomUUID().toString();

  private final String script = RedissonSupport.script("order_auto_route.lua");

  /**
   * 最近一次由 Lua 脚本返回的集群路由决策（不可变 Map）。
   *
   * <p>保留 Map 而不是类型化对象，是因为 status() 需要把脚本返回的全部字段原样暴露给监控端点。只有定时采样线程会写入，
   * 请求线程只读，因此用 volatile 整体替换即可。启动后第一次采样完成前，默认 SYNC 且不接单。
   */
  private volatile Map<String, Object> latestSnapshot =
      Map.of(KEY_MODE, MODE_SYNC, KEY_ACCEPTING, false, KEY_REASON, REASON_WARMING_UP);

  /** 最近一次成功采样的 System.nanoTime()；0 表示还没有成功采样过。字段名被测试通过反射使用，不要改名。 */
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
      ObjectProvider<BrokerHealthChecker> brokerHealthChecker,
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
    this.brokerHealthChecker = brokerHealthChecker;
    this.json = json;
    this.dataSource = dataSource;
    this.registry = registry;
    Gauge.builder(
            "mall.order.routing.async",
            this,
            router -> router.snapshotSelectsAsync() ? 1 : 0)
        .register(registry);
    Gauge.builder(
            "mall.order.routing.accepting",
            this,
            router -> router.isFreshAndAccepting() ? 1 : 0)
        .register(registry);
  }

  /** 部署配置的提交模式是否为 AUTO；只有 AUTO 才需要本类做压力路由。 */
  public boolean automatic() {
    return SUBMISSION_MODE_AUTO.equals(processing.getSubmissionMode());
  }

  /** 本地快照是否仍在有效期内；从未成功采样过也视为不新鲜。 */
  private boolean fresh() {
    boolean everSampled = sampledAt != 0;
    if (!everSampled) {
      return false;
    }
    long ageNanos = System.nanoTime() - sampledAt;
    return ageNanos < limits.getStaleAfter().toNanos();
  }

  private boolean snapshotSelectsAsync() {
    return MODE_ASYNC.equals(latestSnapshot.get(KEY_MODE));
  }

  private boolean isFreshAndAccepting() {
    return fresh() && Boolean.TRUE.equals(latestSnapshot.get(KEY_ACCEPTING));
  }

  /**
   * 返回当前快照里的模式，不检查快照是否新鲜、是否接单。
   *
   * <p>调用方是 OrderSubmissionPolicy.prepareMode()：用户进入确认页/Agent 生成购买提议时，先给 trace 记一个预计模式。
   * 这一阶段还没有预扣库存，模式选错也没有后果，所以只是"提示"，不需要拒绝请求。真正生效的模式由 {@link #select()} 在
   * 首次预扣库存前重新选择并持久化，之后同一购买编号的重试始终复用那次的结果，不会再随压力变化。
   */
  public String hint() {
    return String.valueOf(latestSnapshot.get(KEY_MODE));
  }

  /**
   * 首次预扣库存前选定最终模式（由 OrderSubmissionPolicy.firstSubmissionMode() 调用）。
   *
   * <p>快照过期或集群决策为暂停接单时返回 503，让用户用原购买编号重试；不能在不知道真实压力的情况下继续受理。
   */
  public String select() {
    if (!isFreshAndAccepting()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "下单服务繁忙，请稍后使用原购买编号重试");
    }
    return hint();
  }

  /** 本实例的异步链路是否可用：消息投递已开启，且 Broker 发送探针最近一次检查为健康。自动路由和固定 ASYNC 模式共用。 */
  public boolean isAsyncReady() {
    if (!messaging.isEnabled()) {
      return false;
    }
    BrokerHealthChecker checker = brokerHealthChecker.getIfAvailable();
    return checker != null && checker.isHealthy();
  }

  /**
   * 定时采样并刷新路由快照（仅 AUTO 模式）。
   *
   * <p>异步积压需要对 trace 表做聚合查询，所以只在这里后台采样，绝不放到每个下单请求里。采样过程中任何异常都走
   * {@link #handleSamplingFailure}：本机暂停接单并告知其他实例，不能把"读不到数据"当成"零压力"继续接单。
   */
  @Scheduled(
      fixedDelayString = "${app.order-routing.sample-interval-ms:1000}",
      initialDelayString = "${app.order-routing.initial-delay-ms:500}")
  public void sample() {
    if (!automatic()) {
      return;
    }
    try {
      requireSharedInventoryReservation();
      Map<String, Object> localSample = collectLocalSample();
      String clusterDecisionJson = reportSample(localSample);
      applyClusterDecision(clusterDecisionJson);
    } catch (Exception ex) {
      handleSamplingFailure(ex);
    }
  }

  /** AUTO 会在同步、异步之间切换，两条链路必须共用 Redis 库存预扣，否则同步建单会绕过异步已占的库存。 */
  private void requireSharedInventoryReservation() {
    if (!inventory.isEnabled()) {
      throw new IllegalStateException("AUTO 必须启用共享库存预扣，不能让同步绕过异步预占");
    }
  }

  /** 汇总本机压力样本，字段名需与 order_auto_route.lua 读取的字段一致。 */
  private Map<String, Object> collectLocalSample() throws SQLException {
    Map<String, Object> sample = new LinkedHashMap<>(window.snapshot());
    sample.put("inflight", metrics.inFlight());
    sample.put("permits", processing.getMaxConcurrent());
    sample.put("capacity", limits.getSyncQpsPerInstance());
    putDatabasePoolPressure(sample);
    putAsyncBacklog(sample);
    sample.put("healthy", true);
    sample.put("asyncReady", isAsyncReady());
    return sample;
  }

  /** 数据库连接池使用率和等待线程数；非 Hikari 数据源或连接池尚未启动时记为 0。 */
  private void putDatabasePoolPressure(Map<String, Object> sample) throws SQLException {
    double usage = 0;
    int waiting = 0;
    if (dataSource.isWrapperFor(HikariDataSource.class)) {
      HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
      HikariPoolMXBean poolStats = pool.getHikariPoolMXBean();
      if (poolStats != null) {
        usage = poolStats.getActiveConnections() / (double) Math.max(1, pool.getMaximumPoolSize());
        waiting = poolStats.getThreadsAwaitingConnection();
      }
    }
    sample.put("databaseUsage", usage);
    sample.put("databaseWaiters", waiting);
  }

  /** 异步待建单数量，以及最早一笔待建单已经等待的秒数。 */
  private void putAsyncBacklog(Map<String, Object> sample) {
    Map<String, Object> backlog = traces.backlog();
    sample.put("pending", ((Number) backlog.getOrDefault("pending", 0)).longValue());
    LocalDateTime oldestAcceptedAt = toLocalDateTime(backlog.get("oldest"));
    long oldestSeconds;
    if (oldestAcceptedAt == null) {
      oldestSeconds = 0;
    } else {
      oldestSeconds = Math.max(0, Duration.between(oldestAcceptedAt, LocalDateTime.now()).getSeconds());
    }
    sample.put("oldestSeconds", oldestSeconds);
  }

  /** 不同数据库驱动对时间列返回的类型不同（Timestamp 或 LocalDateTime），统一转换；其他类型视为没有积压。 */
  private static LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime;
    }
    return null;
  }

  /** 把本机样本交给 Lua 脚本汇总，返回脚本计算出的集群决策 JSON。 */
  private String reportSample(Map<String, Object> localSample) {
    String decisionJson = evalRoutingScript(localSample, configuration());
    if (decisionJson == null) {
      throw new IllegalStateException("缺少路由采样结果");
    }
    return decisionJson;
  }

  /** 保存新的集群决策并刷新采样时间；如果本次发生了模式切换，记录指标和日志。 */
  private void applyClusterDecision(String decisionJson) {
    latestSnapshot =
        Collections.unmodifiableMap(
            json.readValue(decisionJson, new TypeReference<Map<String, Object>>() {}));
    sampledAt = System.nanoTime();
    boolean modeChanged = Boolean.TRUE.equals(latestSnapshot.get(KEY_TRANSITION));
    if (modeChanged) {
      registry.counter("mall.order.routing.transitions", "mode", hint()).increment();
      log.info(
          "自动下单模式切换：mode={},qps={},p95={}ms,dbUsage={}",
          hint(),
          latestSnapshot.get("qps"),
          latestSnapshot.get("syncP95Ms"),
          latestSnapshot.get("databaseUsage"));
    }
  }

  /**
   * 采样失败：保留当前模式但本机暂停接单，并尽量把本机标记为不健康上报给其他实例。
   *
   * <p>连续失败时只在第一次打印告警，避免每秒刷日志。
   */
  private void handleSamplingFailure(Exception ex) {
    boolean alreadyUnavailable = REASON_SAMPLING_UNAVAILABLE.equals(latestSnapshot.get(KEY_REASON));
    if (!alreadyUnavailable) {
      log.warn("下单压力采样不可用，暂停新的受理：{}", ex.getClass().getSimpleName());
    }
    latestSnapshot =
        Map.of(KEY_MODE, hint(), KEY_ACCEPTING, false, KEY_REASON, REASON_SAMPLING_UNAVAILABLE);
    registry.counter("mall.order.routing.errors").increment();
    reportUnhealthyBestEffort();
  }

  /**
   * 尽量通知其他实例本机采样失败：上报一份全零、healthy=false 的样本，覆盖本机在 Redis 中上一次的低压力样本，
   * 避免旧样本继续参与集群决策。
   */
  private void reportUnhealthyBestEffort() {
    try {
      Map<String, Object> unhealthy = new LinkedHashMap<>();
      unhealthy.put("qps", 0);
      unhealthy.put("capacity", limits.getSyncQpsPerInstance());
      unhealthy.put("inflight", 0);
      unhealthy.put("permits", processing.getMaxConcurrent());
      unhealthy.put("syncSamples", 0);
      unhealthy.put("syncP95Ms", 0);
      unhealthy.put("databaseUsage", 0);
      unhealthy.put("databaseWaiters", 0);
      unhealthy.put("pending", 0);
      unhealthy.put("oldestSeconds", 0);
      unhealthy.put("healthy", false);
      unhealthy.put("asyncReady", false);
      evalRoutingScript(unhealthy, configuration());
    } catch (Exception ignored) {
      /* 协调 Redis 也故障时无法通知其他实例；各实例的本地快照会在 stale-after 后过期并停止接单。 */
    }
  }

  /** 执行路由 Lua 脚本。三个 key 带相同 hash tag {routing}，保证在 Redis Cluster 中落在同一槽位。 */
  private String evalRoutingScript(Map<String, Object> nodeSample, Map<String, Object> config) {
    String keyPrefix = processing.getNamespace() + "{routing}:";
    return RedissonSupport.eval(
        redis.getObject(),
        script,
        RScript.ReturnType.VALUE,
        List.of(keyPrefix + "nodes", keyPrefix + "samples", keyPrefix + "state"),
        instance,
        json.writeValueAsString(nodeSample),
        json.writeValueAsString(config));
  }

  /** 传给 Lua 脚本的路由阈值配置，字段名需与脚本读取的字段一致。 */
  private Map<String, Object> configuration() {
    Map<String, Object> config = new LinkedHashMap<>();
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

  /** 监控端点使用的路由状态。非 AUTO 模式返回固定配置；AUTO 模式返回最新集群决策加上本机信息。 */
  public Map<String, Object> status() {
    if (!automatic()) {
      return manualModeStatus();
    }
    Map<String, Object> result = new LinkedHashMap<>(latestSnapshot);
    result.put("automatic", automatic());
    result.put("fresh", fresh());
    result.put("configuredMode", processing.getSubmissionMode());
    result.put("instance", instance);
    return result;
  }

  /** 固定 SYNC 总是接单；固定 ASYNC 只在本机异步链路可用时接单。 */
  private Map<String, Object> manualModeStatus() {
    String mode = processing.getSubmissionMode();
    boolean accepting = !MODE_ASYNC.equals(mode) || isAsyncReady();
    String reason = accepting ? REASON_MANUAL_MODE : REASON_ASYNC_UNAVAILABLE;
    return Map.of(
        "automatic",
        false,
        KEY_MODE,
        mode,
        "configuredMode",
        mode,
        KEY_ACCEPTING,
        accepting,
        KEY_REASON,
        reason,
        "fresh",
        true,
        "instance",
        instance);
  }
}
