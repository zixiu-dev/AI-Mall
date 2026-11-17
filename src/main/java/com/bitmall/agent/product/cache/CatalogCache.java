package com.bitmall.agent.product.cache;

import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.config.CatalogProtectionProperties;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品公共查询的多级缓存入口。 先判断是否为热点，再尝试本机 Caffeine；本地未命中后检查商品 ID Bloom，再读 Redis，最后调用传入的数据库查询函数；使用 Redisson
 * RLock 跨实例合并重建，同时限制并发回源，避免缓存故障时数据库被压垮。
 */
@Component
public class CatalogCache {
  /** 依赖组件：读取 app.catalog-cache 配置。 */
  private final CatalogCacheProperties properties;

  /** 依赖组件：负责商品缓存与 Redis 的交互。 */
  private final RedisCatalogStore redis;

  /** 依赖组件：热点识别接口。 */
  private final HotKeyDetector hotKeys;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper json;

  /** 依赖组件：记录缓存运行计数，例如本机命中、Redis 命中、数据库回源和失败次数。 */
  private final CacheMetrics metrics;

  private final CatalogBloomFilter bloom;
  private final CatalogProtectionProperties protection;
  private final CacheTtlPolicy ttls;
  private final com.bitmall.agent.product.config.CatalogLocalProperties localPolicy;

  /** 本机 Caffeine 缓存，仅保存仍为热点且修订号有效的商品数据。 */
  private final Cache<String, LocalValue> local;

  /** 数据库回源信号量，限制所有 key 合计的并发加载数。 */
  private final Semaphore loads;

  /** 本机修订号，缓存失效时递增，防止旧查询重新写入已失效的内容。 */
  private final AtomicLong revision = new AtomicLong();

  /** 构造缓存组件，保存依赖并初始化 Caffeine 和数据库回源信号量。这里只创建保护机制，实际商品在查询时才加载。 */
  @Autowired
  public CatalogCache(
      CatalogCacheProperties properties,
      RedisCatalogStore redis,
      HotKeyDetector hotKeys,
      ObjectMapper json,
      CacheMetrics metrics,
      CatalogBloomFilter bloom,
      CatalogProtectionProperties protection,
      CacheTtlPolicy ttls,
      com.bitmall.agent.product.config.CatalogLocalProperties localPolicy) {
    this.localPolicy = localPolicy;
    this.bloom = bloom;
    this.protection = protection;
    this.ttls = ttls;
    this.properties = properties;
    this.redis = redis;
    this.hotKeys = hotKeys;
    this.json = json;
    this.metrics = metrics;
    local =
        Caffeine.newBuilder()
            .maximumSize(properties.getLocalMaximumSize())
            .expireAfterWrite(properties.getLocalTtl())
            .build();
    loads = new Semaphore(properties.getMaximumConcurrentLoads());
  }

  /** 保留独立缓存测试的构造入口，默认仍使用真实热点提升策略。 */
  public CatalogCache(
      CatalogCacheProperties properties,
      RedisCatalogStore redis,
      HotKeyDetector hotKeys,
      ObjectMapper json,
      CacheMetrics metrics,
      CatalogBloomFilter bloom,
      CatalogProtectionProperties protection,
      CacheTtlPolicy ttls) {
    this(
        properties,
        redis,
        hotKeys,
        json,
        metrics,
        bloom,
        protection,
        ttls,
        new com.bitmall.agent.product.config.CatalogLocalProperties("HOTKEY"));
  }

  public CatalogCache(
      CatalogCacheProperties properties,
      RedisCatalogStore redis,
      HotKeyDetector hotKeys,
      ObjectMapper json,
      CacheMetrics metrics) {
    this(
        properties,
        redis,
        hotKeys,
        json,
        metrics,
        null,
        new CatalogProtectionProperties(false, 0, 0, 0, null, null, null),
        new CacheTtlPolicy(new CatalogProtectionProperties(false, 0, 0, 0, null, null, null)));
  }

  /** 商品查询的统一缓存流程：检查事务和开关，查本机缓存、详情 Bloom、Redis，最后受控回源数据库。 loader 是实际数据库查询函数。 */
  public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
    if (!properties.isEnabled() || TransactionSynchronizationManager.isActualTransactionActive()) {
      metrics.increment("bypass_load");
      return loader.get();
    }
    boolean hot =
        "ALL".equals(localPolicy.getMode())
            || ("HOTKEY".equals(localPolicy.getMode()) && hotKeys.isHot("mall:" + key));
    T hit = readLocal(key, type, hot);
    if (hit != null) return hit;
    // 本地未命中后先过滤商品 ID；明确不存在时连共享详情缓存也不读取。
    // 搜索条件不适用商品 ID Bloom；过滤器未知/故障由 mightContain 放行。
    if (bloom != null
        && type == DetailValue.class
        && key.startsWith("product:")
        && !bloom.mightContain(Long.parseLong(key.substring(8))))
      return type.cast(new DetailValue(null));
    // 可能存在才读取共享缓存；命中仍不排队竞争重建锁。
    long readRevision = revision.get();
    RedisCatalogStore.Lookup found = redis.lookup(key);
    hit = readRedis(key, type, hot, found, readRevision);
    if (hit != null) return hit;
    metrics.increment("rebuild_requested");
    String token = null;
    try {
      long until = System.nanoTime() + protection.getLockWait().toNanos();
      while (true) {
        Boolean owner = redis.acquireLoad(key);
        if (owner == null) break; // Redis 故障走本机并发受限回源，不能无限等锁。
        if (owner) {
          token = redis.ownerToken();
          metrics.increment("rebuild_lock_acquired");
          break;
        }
        metrics.increment("rebuild_wait");
        readRevision = revision.get();
        found = redis.lookup(key);
        hit = readRedis(key, type, hot, found, readRevision);
        if (hit != null) return hit;
        if (System.nanoTime() >= until) throw busy();
        try {
          Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(10, 31));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw busy();
        }
      }
      // 分布式锁拿到后再次检查：另一个实例可能在本次抢锁前刚刚回填完。
      long before = revision.get();
      readRevision = revision.get();
      found = redis.lookup(key);
      hit = readRedis(key, type, hot, found, readRevision);
      if (hit != null) {
        metrics.increment("rebuild_avoided_after_redis_lock");
        return hit;
      }
      if (!loads.tryAcquire()) throw busy();
      T value;
      try {
        metrics.increment("database_load");
        value = loader.get();
      } finally {
        loads.release();
      }
      boolean negative = value instanceof DetailValue detail && detail.getProduct() == null;
      if (negative) metrics.increment("negative_load");
      String encoded = encode(value);
      Duration effective = ttls.jitter(negative ? properties.getNegativeTtl() : ttl);
      if (encoded != null && found != null && before == revision.get()) {
        boolean stored = redis.fillOwned(key, found.getGeneration(), encoded, effective, token);
        if (stored) metrics.increment("rebuild_filled");
        if (stored && hot && !negative && before == revision.get())
          local.put(key, new LocalValue(before, encoded));
        if (!stored) metrics.increment("fill_skipped");
      }
      return value;
    } finally {
      redis.releaseLoad(key, token);
    }
  }

  /** 损坏值不作为命中；热点只提升有效的商品数据，空结果保持短 TTL。 */
  private <T> T readRedis(
      String key, Class<T> type, boolean hot, RedisCatalogStore.Lookup found, long before) {
    if (found == null || found.getValue() == null) return null;
    T value = decode(found.getValue(), type);
    if (value == null) {
      redis.discard(key, found.getGeneration());
      metrics.increment("corrupt_entry");
      return null;
    }
    metrics.increment("redis_hit");
    if (value instanceof DetailValue d && d.getProduct() == null) metrics.increment("negative_hit");
    else if (hot && before == revision.get() && redis.available())
      local.put(key, new LocalValue(before, found.getValue()));
    return value;
  }

  /** 只有当前 key 仍为热点、Redis 可用且本地修订号有效时，才接受本机缓存。 */
  private <T> T readLocal(String key, Class<T> type, boolean hot) {
    if (!hot || !redis.available()) {
      local.invalidate(key);
      return null;
    }
    LocalValue value = local.getIfPresent(key);
    if (value == null) return null;
    if (value.getRevision() != revision.get()) {
      local.invalidate(key);
      return null;
    }
    T decoded = decode(value.getJson(), type);
    if (decoded == null) local.invalidate(key);
    else metrics.increment("local_hit");
    return decoded;
  }

  /** 把业务对象转成 JSON 存入缓存；序列化失败只跳过缓存，不让商品查询因此失败。 */
  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      metrics.increment("codec_error");
      return null;
    }
  }

  /** 按调用者指定的类型恢复缓存对象；损坏内容返回空，由上层重新查询。 */
  private <T> T decode(String value, Class<T> type) {
    try {
      JavaType target = json.constructType(type);
      return json.readValue(value, target);
    } catch (Exception ex) {
      return null;
    }
  }

  /** 生成繁忙响应；等待锁或回源并发超过上限时，让请求明确失败而不是无限堆积。 */
  private ResponseStatusException busy() {
    metrics.increment("load_rejected");
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品查询繁忙，请稍后重试");
  }

  /** 仅在业务事务提交后处理商品变更：清空本机缓存，并通知 Redis 切换缓存代际。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void committed(CatalogChanged ignored) {
    invalidateLocal();
    redis.invalidate();
    metrics.increment("committed_invalidation");
  }

  /** 增加本地修订号并清空 Caffeine，阻止旧查询继续回填过期结果。 */
  public void invalidateLocal() {
    revision.incrementAndGet();
    local.invalidateAll();
    metrics.increment("local_invalidation");
  }

  /** 汇总缓存容量、过期配置、Redis 状态、热点状态和查询计数。 */
  public Map<String, Object> status() {
    return Map.of(
        "localMode",
        localPolicy.getMode(),
        "bloom",
        bloom == null ? Map.of("enabled", false) : bloom.status(),
        "ttlJitterRatio",
        protection.getTtlJitterRatio(),
        "redis",
        redis.status(),
        "hotkey",
        hotKeys.status(),
        "counters",
        metrics.snapshot(),
        "localEntries",
        local.estimatedSize(),
        "localTtlMillis",
        properties.getLocalTtl().toMillis(),
        "detailTtlMillis",
        properties.getDetailTtl().toMillis(),
        "searchTtlMillis",
        properties.getSearchTtl().toMillis());
  }

  /** 商品详情缓存的包装对象。 product 为空也能被序列化和短暂缓存，减少重复查询不存在商品造成的数据库压力。 */
  public static final class DetailValue {
    /** 查到的商品详情；为空时表示商品不存在。 */
    private final ProductDetailVO product;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public DetailValue(@JsonProperty("product") ProductDetailVO product) {
      this.product = product;
    }

    /** 获取查到的商品详情；为空时表示商品不存在。 */
    public ProductDetailVO getProduct() {
      return product;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      DetailValue that = (DetailValue) other;
      return Objects.equals(product, that.product);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
      return Objects.hash(product);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
      return "DetailValue[" + "product=" + product + "]";
    }
  }

  /** 本机缓存中的一条内部数据。 修订号用来识别旧缓存，json 保存商品查询结果的序列化内容。 */
  private static final class LocalValue {
    /** 本机缓存修订号，用于识别已经失效的旧值。 */
    private final long revision;

    /** 已经序列化的商品数据。 */
    private final String json;

    /** 保存这一次内部计算的数据，创建后通过 Getter 读取。 这个内部对象由 Java 代码直接创建，不需要从 HTTP 请求或缓存 JSON 恢复。 */
    public LocalValue(long revision, String json) {
      this.revision = revision;
      this.json = json;
    }

    /** 获取本机缓存修订号，用于识别已经失效的旧值。 */
    public long getRevision() {
      return revision;
    }

    /** 获取已经序列化的商品数据。 */
    public String getJson() {
      return json;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      LocalValue that = (LocalValue) other;
      return Objects.equals(revision, that.revision) && Objects.equals(json, that.json);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
      return Objects.hash(revision, json);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
      return "LocalValue[" + "revision=" + revision + ", json=" + json + "]";
    }
  }
}
