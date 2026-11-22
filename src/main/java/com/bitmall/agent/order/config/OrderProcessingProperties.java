package com.bitmall.agent.order.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Locale;
import java.util.Set;

/** 下单链路配置（app.order-processing）：交易 Redis 连接、锁、各种有效期和并发上限。 */
@Getter
@ConfigurationProperties("app.order-processing")
public class OrderProcessingProperties {
  /** 交易 Redis 主机，默认 127.0.0.1。交易 Redis 与商品缓存 Redis 是两套实例。 */
  private final String redisHost;

  /** 交易 Redis 端口，默认 6381。 */
  private final int redisPort;

  /** 交易 Redis 密码，默认空。不要写进日志。 */
  private final String redisPassword;

  /** 交易 Redis 逻辑库编号，默认 0。 */
  private final int redisDatabase;

  /** 交易 Redis 中所有库存、锁、幂等标记 key 的前缀，用来隔离不同环境。 */
  private final String namespace;

  /** 是否启用分布式锁；离线 demo 可只使用数据库兜底。 */
  private final boolean locksEnabled;

  /** Redis 锁故障时是否允许交给数据库 CAS 兜底；业务仍由数据库状态和唯一键保护。 */
  private final boolean databaseFallback;

  /** 购买编号（trace）在提交前的有效期（秒），默认 600；过期后需要重新结算。 */
  private final int traceTtlSeconds;

  /** 建单方式：SYNC（默认，同步建单）、ASYNC（排队异步建单）、AUTO（按压力自动切换）。由后端决定，不接受前端指定。 */
  private final String submissionMode;

  /** 异步受理后最长的排队处理时间（秒），默认 600；超时未建单则记为失败。与购买编号有效期是两回事。 */
  private final int asyncTimeoutSeconds;

  /** 订单支付期限（秒），默认 900；超时未支付会自动关单并释放库存和券。 */
  private final int paymentTtlSeconds;

  /** 单实例同时处理的下单请求上限，默认 32；超过直接返回 429。 */
  private final int maxConcurrent;

  /** 下单 P95 耗时目标（毫秒），默认 500，只用于监控展示和告警。 */
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
        submissionMode == null ? "SYNC" : submissionMode.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("SYNC", "ASYNC", "AUTO").contains(this.submissionMode)) {
      throw new IllegalArgumentException("submission-mode 只能配置 SYNC、ASYNC 或 AUTO");
    }
    this.asyncTimeoutSeconds = asyncTimeoutSeconds > 0 ? asyncTimeoutSeconds : 600;
    this.paymentTtlSeconds = paymentTtlSeconds > 0 ? paymentTtlSeconds : 900;
    this.maxConcurrent = maxConcurrent > 0 ? maxConcurrent : 32;
    this.latencyTargetMs = latencyTargetMs > 0 ? latencyTargetMs : 500;
  }
}
