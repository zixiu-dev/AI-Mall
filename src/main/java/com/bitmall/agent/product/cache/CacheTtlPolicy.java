package com.bitmall.agent.product.cache;

import com.bitmall.agent.product.config.CatalogProtectionProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 在基础 TTL 上增加 0～配置比例的随机延迟，长 TTL 也能分散过期，空值使用自己的短基础 TTL。 */
@Component
@RequiredArgsConstructor
public class CacheTtlPolicy {
  /** 读取布隆过滤器、空值缓存、重建并发和 TTL 抖动的保护配置。 */
  private final CatalogProtectionProperties properties;

  public Duration jitter(Duration base) {
    long millis = Math.max(1, base.toMillis());
    long extra = (long) (millis * properties.getTtlJitterRatio());
    return Duration.ofMillis(
        millis + (extra > 0 ? ThreadLocalRandom.current().nextLong(extra + 1) : 0));
  }
}
