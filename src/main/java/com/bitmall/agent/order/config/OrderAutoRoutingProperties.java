package com.bitmall.agent.order.config;

import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AUTO 下单模式的路由阈值（app.order-routing），由 order_auto_route.lua 在 Redis 里统一判定。
 *
 * <p>每秒评估一次集群压力：任一指标达到"高"阈值算一次高压采样，所有指标都低于"低"阈值算一次低压采样。 连续高压 {@code highSamples} 次从同步切到异步，连续低压
 * {@code lowSamples} 次切回同步。 高低两套阈值之间留有间隔（滞回）， 加上切换冷却时间，避免模式在临界点来回抖动。
 */
@Getter
@ConfigurationProperties("app.order-routing")
public class OrderAutoRoutingProperties {
  /** 单实例同步下单的 QPS 容量，默认 60，由压测确定。集群容量 = 各在线实例之和；总 QPS 达到容量即为高压，低于一半才算低压。 */
  private final int syncQpsPerInstance;

  /** 连续多少次高压采样后切换到异步，默认 3。 */
  private final int highSamples;

  /** 连续多少次低压采样后切回同步，默认 10（比升级更保守）。 */
  private final int lowSamples;

  /** 两次模式切换之间的最短间隔，默认 30 秒。 */
  private final Duration cooldown;

  /** 压力快照的有效期，默认 5 秒，至少 2 秒。实例超过这个时间没上报就被移出集群统计；本机快照过期则暂停受理下单。 */
  private final Duration staleAfter;

  /** 同步下单 P95 耗时的高压阈值（毫秒），默认 300。 */
  private final int syncP95HighMs;

  /** 同步下单 P95 耗时的低压阈值（毫秒），默认 150，必须低于高压阈值。 */
  private final int syncP95LowMs;

  /** 实例同步样本数至少达到这个值，它的 P95 才参与判定，默认 20；避免几个慢请求就触发切换。 */
  private final int minimumSyncSamples;

  /** 在途下单数占并发许可的比例，高压阈值，默认 0.7。 */
  private final double inFlightHigh;

  /** 在途下单数占并发许可的比例，低压阈值，默认 0.3。 */
  private final double inFlightLow;

  /** 数据库连接池使用率高压阈值，默认 0.8（取各实例最大值；有线程在等连接也直接算高压）。 */
  private final double databaseHigh;

  /** 数据库连接池使用率低压阈值，默认 0.5。 */
  private final double databaseLow;

  /** 异步待建单积压达到这个数量时暂停受理新下单，默认 1000。 */
  private final int maxPending;

  /** 异步积压降到这个数量以下才可能算低压、切回同步，默认 100。 */
  private final int recoverPending;

  /** 最早一笔异步待建单等待超过这个秒数时暂停受理新下单，默认 20。 */
  private final int maxQueueAgeSeconds;

  public OrderAutoRoutingProperties(
      int syncQpsPerInstance,
      int highSamples,
      int lowSamples,
      Duration cooldown,
      Duration staleAfter,
      int syncP95HighMs,
      int syncP95LowMs,
      int minimumSyncSamples,
      double inFlightHigh,
      double inFlightLow,
      double databaseHigh,
      double databaseLow,
      int maxPending,
      int recoverPending,
      int maxQueueAgeSeconds) {
    this.syncQpsPerInstance = syncQpsPerInstance > 0 ? syncQpsPerInstance : 60;
    this.highSamples = highSamples > 0 ? highSamples : 3;
    this.lowSamples = lowSamples > 0 ? lowSamples : 10;
    this.cooldown = cooldown == null ? Duration.ofSeconds(30) : cooldown;
    this.staleAfter = staleAfter == null ? Duration.ofSeconds(5) : staleAfter;
    this.syncP95HighMs = syncP95HighMs > 0 ? syncP95HighMs : 300;
    this.syncP95LowMs = syncP95LowMs > 0 ? syncP95LowMs : 150;
    this.minimumSyncSamples = minimumSyncSamples > 0 ? minimumSyncSamples : 20;
    this.inFlightHigh = inFlightHigh > 0 ? inFlightHigh : .7;
    this.inFlightLow = inFlightLow > 0 ? inFlightLow : .3;
    this.databaseHigh = databaseHigh > 0 ? databaseHigh : .8;
    this.databaseLow = databaseLow > 0 ? databaseLow : .5;
    this.maxPending = maxPending > 0 ? maxPending : 1000;
    this.recoverPending = recoverPending > 0 ? recoverPending : 100;
    this.maxQueueAgeSeconds = maxQueueAgeSeconds > 0 ? maxQueueAgeSeconds : 20;

    // 每组"低"阈值必须严格低于"高"阈值，否则同一个采样可能既算高压又算低压，滞回失效。
    boolean lowBelowHigh =
        this.syncP95LowMs < this.syncP95HighMs
            && this.inFlightLow < this.inFlightHigh
            && this.databaseLow < this.databaseHigh
            && this.recoverPending < this.maxPending;
    boolean validDurations = !this.cooldown.isNegative() && this.staleAfter.toMillis() >= 2000;
    if (!lowBelowHigh || !validDurations) {
      throw new IllegalArgumentException("自动路由恢复阈值必须低于升级阈值，采样有效期至少两秒");
    }
  }
}
