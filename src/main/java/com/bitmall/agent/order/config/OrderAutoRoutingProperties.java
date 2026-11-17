package com.bitmall.agent.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AUTO 模式的容量和滞回参数。每实例同步容量由压测确定，扩容时协调器会累加实例容量。 */
@ConfigurationProperties("app.order-routing")
public class OrderAutoRoutingProperties {
  private final int syncQpsPerInstance;
  private final int highSamples;
  private final int lowSamples;
  private final Duration cooldown;
  private final Duration staleAfter;
  private final int syncP95HighMs;
  private final int syncP95LowMs;
  private final int minimumSyncSamples;
  private final double inFlightHigh;
  private final double inFlightLow;
  private final double databaseHigh;
  private final double databaseLow;
  private final int maxPending;
  private final int recoverPending;
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
    if (this.syncP95LowMs >= this.syncP95HighMs
        || this.inFlightLow >= this.inFlightHigh
        || this.databaseLow >= this.databaseHigh
        || this.recoverPending >= this.maxPending
        || this.cooldown.isNegative()
        || this.staleAfter.toMillis() < 2000)
      throw new IllegalArgumentException("自动路由恢复阈值必须低于升级阈值，采样有效期至少两秒");
  }

  public int getSyncQpsPerInstance() {
    return syncQpsPerInstance;
  }

  public int getHighSamples() {
    return highSamples;
  }

  public int getLowSamples() {
    return lowSamples;
  }

  public Duration getCooldown() {
    return cooldown;
  }

  public Duration getStaleAfter() {
    return staleAfter;
  }

  public int getSyncP95HighMs() {
    return syncP95HighMs;
  }

  public int getSyncP95LowMs() {
    return syncP95LowMs;
  }

  public int getMinimumSyncSamples() {
    return minimumSyncSamples;
  }

  public double getInFlightHigh() {
    return inFlightHigh;
  }

  public double getInFlightLow() {
    return inFlightLow;
  }

  public double getDatabaseHigh() {
    return databaseHigh;
  }

  public double getDatabaseLow() {
    return databaseLow;
  }

  public int getMaxPending() {
    return maxPending;
  }

  public int getRecoverPending() {
    return recoverPending;
  }

  public int getMaxQueueAgeSeconds() {
    return maxQueueAgeSeconds;
  }
}
