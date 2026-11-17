package com.bitmall.agent.product.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.product.config.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 检查详情过滤顺序与边界，防止明确不存在的商品仍读取共享缓存或触发数据库回源。 */
class CatalogCacheReadOrderTest {
  private final RedisCatalogStore redis = mock(RedisCatalogStore.class);
  private final CatalogBloomFilter bloom = mock(CatalogBloomFilter.class);
  private CatalogCache cache;

  @BeforeEach
  void setup() {
    var properties =
        new CatalogCacheProperties(true, "order-test", null, null, null, null, 0, null, 0);
    var protection = new CatalogProtectionProperties(true, 0, 0, 0, null, null, null);
    cache =
        new CatalogCache(
            properties,
            redis,
            mock(HotKeyDetector.class),
            new ObjectMapper(),
            new CacheMetrics(new SimpleMeterRegistry()),
            bloom,
            protection,
            new CacheTtlPolicy(protection),
            new CatalogLocalProperties("OFF"));
  }

  @Test
  void definiteAbsenceSkipsSharedCacheLockAndDatabaseEvenIfSharedEntryExists() {
    when(bloom.mightContain(901)).thenReturn(false);
    // 即便共享缓存有旧条目，明确否定应在读取旧条目前结束。
    when(redis.lookup("product:901"))
        .thenReturn(new RedisCatalogStore.Lookup("g", "{\"product\":null}"));
    var result =
        cache.get(
            "product:901",
            CatalogCache.DetailValue.class,
            Duration.ofSeconds(20),
            () -> {
              throw new AssertionError("明确不存在时不得回源数据库");
            });
    assertThat(result.getProduct()).isNull();
    verify(bloom).mightContain(901);
    verify(redis, never()).lookup(anyString());
    verify(redis, never()).acquireLoad(anyString());
  }

  @Test
  void possiblePresenceChecksBloomBeforeReadingSharedNegativeCache() {
    when(bloom.mightContain(902)).thenReturn(true);
    when(redis.lookup("product:902"))
        .thenReturn(new RedisCatalogStore.Lookup("g", "{\"product\":null}"));
    var result =
        cache.get(
            "product:902",
            CatalogCache.DetailValue.class,
            Duration.ofSeconds(20),
            () -> {
              throw new AssertionError("共享空值命中不得回源");
            });
    assertThat(result.getProduct()).isNull();
    var sequence = inOrder(bloom, redis);
    sequence.verify(bloom).mightContain(902);
    sequence.verify(redis).lookup("product:902");
    verify(redis, never()).acquireLoad(anyString());
  }

  @Test
  void searchDoesNotUseProductIdBloom() {
    when(redis.lookup("search:filters"))
        .thenReturn(new RedisCatalogStore.Lookup("g", "\"cached\""));
    assertThat(
            cache.get(
                "search:filters",
                String.class,
                Duration.ofSeconds(5),
                () -> {
                  throw new AssertionError("搜索命中不回源");
                }))
        .isEqualTo("cached");
    verifyNoInteractions(bloom);
  }
}
