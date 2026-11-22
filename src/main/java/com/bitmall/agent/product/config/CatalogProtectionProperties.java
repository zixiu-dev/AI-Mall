package com.bitmall.agent.product.config;

import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商品缓存的穿透、击穿、雪崩保护参数（app.catalog-protection）。
 *
 * <p>布隆过滤器只记录"哪些商品 ID 存在"，用来挡住查询不存在商品的请求，不缓存商品详情本身。
 */
@ConfigurationProperties("app.catalog-protection")
public class CatalogProtectionProperties {
  /** 是否启用布隆过滤器防穿透，未配置时默认启用。 */
  @Getter private final boolean bloomEnabled;

  /** 布隆过滤器的预期商品数，默认 100 万，上限 2000 万。与误判率一起决定位图大小；改变后旧位图不能再用，需要换命名空间重建。 */
  @Getter private final long expectedProducts;

  /** 布隆过滤器的目标误判率，默认 0.001，取值范围 [0.000001, 1)。 */
  @Getter private final double falsePositiveRate;

  /** 重建布隆过滤器时每批从数据库扫描的商品 ID 数，默认 1000。 */
  @Getter private final int scanBatch;

  /** 已废弃：旧版重建锁的固定租约。重建锁现在由 Redisson 看门狗自动续期，此值只做参数校验，不再生效。 */
  private final Duration lockLease;

  /** 缓存未命中时，等待回源锁（以及布隆过滤器重建锁）的最长时间，默认 1200 毫秒，超时后按降级策略处理。 */
  @Getter private final Duration lockWait;

  /** 缓存 TTL 的随机抖动比例，默认 0.2，即在基础 TTL 上随机增加 0～20%，让同一批 key 不在同一时刻过期。取值 [0, 1]。 */
  @Getter private final double ttlJitterRatio;

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

    boolean bloomParametersValid =
        this.expectedProducts <= 20_000_000
            && this.falsePositiveRate >= 0.000001
            && this.falsePositiveRate < 1;
    boolean jitterValid = !(this.ttlJitterRatio < 0 || this.ttlJitterRatio > 1);
    boolean durationsPositive =
        !this.lockLease.isZero()
            && !this.lockLease.isNegative()
            && !this.lockWait.isNegative()
            && !this.lockWait.isZero();
    if (!bloomParametersValid || !jitterValid || !durationsPositive) {
      throw new IllegalArgumentException("缓存保护参数超出允许范围");
    }
  }

  /** 已废弃，见字段说明。 */
  @Deprecated
  public Duration getLockLease() {
    return lockLease;
  }
}
