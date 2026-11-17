package com.bitmall.agent.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 下单基础设施参数。交易 Redis 与公共缓存分离，同步与异步共用预扣池和数据库库存规则。 */
@ConfigurationProperties("app.order-processing")
public class OrderProcessingProperties {
  /** 独立交易 Redis 的主机。 */
  private final String redisHost;

  /** 独立交易 Redis 的端口。 */
  private final int redisPort;

  /** 独立交易 Redis 的密码。 */
  private final String redisPassword;

  /** 独立交易 Redis 的逻辑库。 */
  private final int redisDatabase;

  /** 所有库存与锁的环境前缀。 */
  private final String namespace;

  /** 是否启用分布式锁；离线 demo 可只使用数据库兜底。 */
  private final boolean locksEnabled;

  /** Redis 锁故障时是否允许交给数据库 CAS 兜底；业务仍由数据库状态和唯一键保护。 */
  private final boolean databaseFallback;

  /** 未提交确认凭证的有效秒数。 */
  private final int traceTtlSeconds;

  /** 后端选择的建单方式，SYNC 默认，ASYNC 可靠排队；不由前端指定。 */
  private final String submissionMode;

  /** 异步受理后的最长处理时间，与用户确认凭证有效期分开。 */
  private final int asyncTimeoutSeconds;

  /** 待支付订单的有效秒数，超时关闭并释放占用。 */
  private final int paymentTtlSeconds;

  /** 单实例同时执行受理流程的上限，超过后快速拒绝。 */
  private final int maxConcurrent;

  /** 用于监控告警的下单 P95 目标毫秒数。 */
  private final int latencyTargetMs;

  /** 绑定普通构造参数并补齐缺省值，不在配置类里建立网络连接。 */
  public OrderProcessingProperties(
      String redisHost,
      int redisPort,
      String redisPassword,
      int redisDatabase,
      String namespace,
      boolean locksEnabled,
      boolean databaseFallback,
      int traceTtlSeconds,
      int paymentTtlSeconds,
      String submissionMode,
      int asyncTimeoutSeconds,
      int maxConcurrent,
      int latencyTargetMs) {
    this.redisHost = redisHost == null ? "127.0.0.1" : redisHost;
    this.redisPort = redisPort > 0 ? redisPort : 6381;
    this.redisPassword = redisPassword == null ? "" : redisPassword;
    this.redisDatabase = redisDatabase;
    this.namespace = namespace == null ? "wowo:order:dev:" : namespace;
    this.locksEnabled = locksEnabled;
    this.databaseFallback = databaseFallback;
    this.traceTtlSeconds = traceTtlSeconds > 0 ? traceTtlSeconds : 600;
    this.submissionMode =
        submissionMode == null ? "SYNC" : submissionMode.trim().toUpperCase(java.util.Locale.ROOT);
    if (!java.util.Set.of("SYNC", "ASYNC", "AUTO").contains(this.submissionMode))
      throw new IllegalArgumentException("submission-mode 只能配置 SYNC、ASYNC 或 AUTO");
    this.asyncTimeoutSeconds = asyncTimeoutSeconds > 0 ? asyncTimeoutSeconds : 120;
    this.paymentTtlSeconds = paymentTtlSeconds > 0 ? paymentTtlSeconds : 900;
    this.maxConcurrent = maxConcurrent > 0 ? maxConcurrent : 32;
    this.latencyTargetMs = latencyTargetMs > 0 ? latencyTargetMs : 500;
  }

  /** 获取库存 Redis 主机。 */
  public String getRedisHost() {
    return redisHost;
  }

  /** 获取库存 Redis 端口。 */
  public int getRedisPort() {
    return redisPort;
  }

  /** 获取连接密码，不在日志中输出。 */
  public String getRedisPassword() {
    return redisPassword;
  }

  /** 获取逻辑库。 */
  public int getRedisDatabase() {
    return redisDatabase;
  }

  /** 获取环境命名空间。 */
  public String getNamespace() {
    return namespace;
  }

  /** 获取分布式锁开关。 */
  public boolean isLocksEnabled() {
    return locksEnabled;
  }

  /** 获取数据库兜底开关。 */
  public boolean isDatabaseFallback() {
    return databaseFallback;
  }

  /** 获取确认凭证有效秒数。 */
  public int getTraceTtlSeconds() {
    return traceTtlSeconds;
  }

  public String getSubmissionMode() {
    return submissionMode;
  }

  public int getAsyncTimeoutSeconds() {
    return asyncTimeoutSeconds;
  }

  /** 获取待支付有效期。 */
  public int getPaymentTtlSeconds() {
    return paymentTtlSeconds;
  }

  /** 获取单实例并发上限。 */
  public int getMaxConcurrent() {
    return maxConcurrent;
  }

  /** 获取耗时告警目标。 */
  public int getLatencyTargetMs() {
    return latencyTargetMs;
  }
}
