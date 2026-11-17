package com.bitmall.agent.product.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 缓存穿透、击穿和雪崩保护参数。布隆过滤器只保存商品 ID 的位信息，不缓存全量商品详情。 */
@ConfigurationProperties("app.catalog-protection")
public class CatalogProtectionProperties {
  private final boolean bloomEnabled;
  private final long expectedProducts;
  private final double falsePositiveRate;
  private final int scanBatch;
  /** 兼容旧构造参数；重建锁已由 Redisson 看门狗续期，不再使用此固定租约。 */
  private final Duration lockLease;
  private final Duration lockWait;
  private final double ttlJitterRatio;

  public CatalogProtectionProperties(
      Boolean bloomEnabled,
      long expectedProducts,
      double falsePositiveRate,
      int scanBatch,
      Duration lockLease,
      Duration lockWait,
      Double ttlJitterRatio) {
    this.bloomEnabled = bloomEnabled == null || bloomEnabled;
    this.expectedProducts = expectedProducts > 0 ? expectedProducts : 1_000_000;
    this.falsePositiveRate = falsePositiveRate > 0 ? falsePositiveRate : .001;
    this.scanBatch = scanBatch > 0 ? scanBatch : 1000;
    this.lockLease = lockLease == null ? Duration.ofSeconds(5) : lockLease;
    this.lockWait = lockWait == null ? Duration.ofMillis(1200) : lockWait;
    this.ttlJitterRatio = ttlJitterRatio == null ? .2 : ttlJitterRatio;
    if (this.expectedProducts > 20_000_000
        || this.falsePositiveRate < 0.000001
        || this.falsePositiveRate >= 1
        || this.ttlJitterRatio < 0
        || this.ttlJitterRatio > 1
        || this.lockLease.isZero()
        || this.lockLease.isNegative()
        || this.lockWait.isNegative()
        || this.lockWait.isZero()) throw new IllegalArgumentException("缓存保护参数超出允许范围");
  }

  public boolean isBloomEnabled() {
    return bloomEnabled;
  }

  public long getExpectedProducts() {
    return expectedProducts;
  }

  public double getFalsePositiveRate() {
    return falsePositiveRate;
  }

  public int getScanBatch() {
    return scanBatch;
  }

  /** 旧配置兼容字段，实际重建采用看门狗。 */
  @Deprecated
  public Duration getLockLease() {
    return lockLease;
  }

  public Duration getLockWait() {
    return lockWait;
  }

  public double getTtlJitterRatio() {
    return ttlJitterRatio;
  }
}
