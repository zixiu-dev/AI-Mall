package com.bitmall.agent.product.cache;

import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.config.CatalogLocalProperties;
import com.bitmall.agent.product.config.CatalogProtectionProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
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
 * 商品公共查询的多级缓存入口。
 *
 * <p>一次查询依次经过：本机 Caffeine（仅热点或 ALL 模式）→ 商品 ID 布隆过滤器（仅商品详情）→ Redis 共享缓存 → 数据库。
 *
 * <p>Redis 未命中时，通过 Redisson 分布式锁让多个实例中只有一个去查数据库并回填，其余实例等待后重读 Redis；同时用本机信号量限制同时回源的数量，
 * 这样即使 Redis 故障导致全部请求回源，数据库也不会被打垮（超出上限的请求直接返回 503）。
 */
@Component
public class CatalogCache {
  private static final String LOCAL_MODE_ALL = "ALL";
  private static final String LOCAL_MODE_HOTKEY = "HOTKEY";

  /** 上报给 HotKey 探测器的 key 前缀。 */
  private static final String HOTKEY_PREFIX = "mall:";

  /** 商品详情缓存 key 的前缀，后面紧跟商品 ID。 */
  private static final String PRODUCT_KEY_PREFIX = "product:";

  /** SKU 报价批量回源的锁 key 前缀；SkuDisplayService 回填时使用同一个逻辑名，两边必须一致。 */
  private static final String OFFERS_KEY_PREFIX = "offers:";

  /** 等待别人重建时的重试间隔：详情查询用 10~30ms 随机值错开重试，SKU 报价用固定 15ms。 */
  private static final long DETAIL_RETRY_MIN_MILLIS = 10;
  private static final long DETAIL_RETRY_MAX_MILLIS_EXCLUSIVE = 31;
  private static final long OFFERS_RETRY_MILLIS = 15;

  /** 读取缓存开关、本地缓存容量和 TTL、详情/搜索/空值缓存 TTL，以及同时回源数据库的并发上限。 */
  private final CatalogCacheProperties properties;
  /** Redis 层缓存读写、回源锁、带凭据的回填和失效广播。 */
  private final RedisCatalogStore redis;
  /** 本地缓存为 HOTKEY 模式时，判断 key 是否为热点（同时上报本次访问）；只有热点才放进本地（L1）缓存。 */
  private final HotKeyDetector hotKeys;
  /** 缓存值与 Java 对象之间的 JSON 序列化。 */
  private final ObjectMapper json;
  /** 记录本地/Redis 命中、数据库回源、重建和失效次数。 */
  private final CacheMetrics metrics;

  /** 商品 ID 布隆过滤器；为 null 时表示不启用（测试构造入口）。 */
  private final CatalogBloomFilter bloom;

  /** 读取等回源锁的最长时间和 TTL 随机抖动比例。 */
  private final CatalogProtectionProperties protection;

  /** 给缓存 TTL 加随机抖动，避免同一批 key 同时过期。 */
  private final CacheTtlPolicy ttls;

  /** 本地缓存模式：HOTKEY 只缓存热点商品，ALL 缓存全部，OFF 不用本地缓存。 */
  private final CatalogLocalProperties localPolicy;

  /** 本机 Caffeine 缓存，存放序列化后的 JSON。 */
  private final Cache<String, String> local;

  /** 限制本机同时回源数据库的请求数，所有 key 共用这一个额度。 */
  private final Semaphore loads;

  /**
   * 本机缓存修订号，每次本地失效时加 1。
   *
   * <p>查询在读 Redis 或查数据库之前先记下修订号，写入本地缓存前再比对：如果期间发生过失效，说明手里的值可能是旧的，就不再写入本地。
   */
  private final AtomicLong revision = new AtomicLong();

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
      CatalogLocalProperties localPolicy) {
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

  /** 测试用构造入口：本地缓存模式固定为 HOTKEY。 */
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
        new CatalogLocalProperties(LOCAL_MODE_HOTKEY));
  }

  /** 测试用构造入口：不启用布隆过滤器，保护参数全部取默认值。 */
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

  /**
   * 按多级缓存流程读取一个值。
   *
   * <p>缓存关闭或处在数据库事务中时直接调用 loader：事务内可能读到未提交数据，不能写进共享缓存。
   *
   * @param loader 真正查询数据库的函数，只在所有缓存都未命中时调用
   */
  public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
    boolean mustBypassCache =
        !properties.isEnabled() || TransactionSynchronizationManager.isActualTransactionActive();
    if (mustBypassCache) {
      metrics.increment("bypass_load");
      return loader.get();
    }

    boolean hot = allowsLocalCache(key);
    T localHit = readLocal(key, type, hot);
    if (localHit != null) {
      return localHit;
    }

    // 布隆过滤器明确判定不存在时，直接返回空商品，连 Redis 也不读。
    if (isKnownMissingProduct(key, type)) {
      return type.cast(new DetailValue(null));
    }

    // Redis 命中就直接返回，不参与重建锁的竞争。
    long readRevision = revision.get();
    RedisCatalogStore.Lookup found = redis.lookup(key);
    T redisHit = readRedis(key, type, hot, found, readRevision);
    if (redisHit != null) {
      return redisHit;
    }

    metrics.increment("rebuild_requested");
    return loadWithRebuildLock(key, type, ttl, loader, hot);
  }

  /** ALL 模式总是允许本地缓存；HOTKEY 模式只有被探测为热点时才允许（判断的同时也会上报这次访问）。 */
  private boolean allowsLocalCache(String key) {
    String mode = localPolicy.getMode();
    if (LOCAL_MODE_ALL.equals(mode)) {
      return true;
    }
    return LOCAL_MODE_HOTKEY.equals(mode) && hotKeys.isHot(HOTKEY_PREFIX + key);
  }

  /**
   * 只有商品详情查询才用布隆过滤器判断；搜索等其他查询的 key 不是商品 ID，不适用。
   *
   * <p>过滤器未就绪或故障时 mightContain 会返回 true（放行），所以这里返回 true 一定代表"确定不存在"。
   */
  private boolean isKnownMissingProduct(String key, Class<?> type) {
    boolean isProductDetail =
        bloom != null && type == DetailValue.class && key.startsWith(PRODUCT_KEY_PREFIX);
    if (!isProductDetail) {
      return false;
    }
    long productId = Long.parseLong(key.substring(PRODUCT_KEY_PREFIX.length()));
    return !bloom.mightContain(productId);
  }

  /**
   * Redis 未命中后的重建流程：抢分布式锁 → 拿到锁后再读一次 Redis → 受限回源数据库 → 带锁回填 Redis 和本地缓存。
   *
   * <p>没抢到锁时说明别的实例正在重建，就短暂休眠后重读 Redis，直到读到结果、抢到锁，或超过等待时间返回 503。
   *
   * <p>Redis 故障时（acquireLoad 返回 null）不再等锁，直接走本机信号量限流的回源，避免请求无限挂起。
   */
  private <T> T loadWithRebuildLock(
      String key, Class<T> type, Duration ttl, Supplier<T> loader, boolean hot) {
    String token = null;
    try {
      long deadline = System.nanoTime() + protection.getLockWait().toNanos();
      while (true) {
        Boolean acquired = redis.acquireLoad(key);
        if (acquired == null) {
          break;
        }
        if (acquired) {
          token = redis.ownerToken();
          metrics.increment("rebuild_lock_acquired");
          break;
        }
        metrics.increment("rebuild_wait");
        long waitRevision = revision.get();
        RedisCatalogStore.Lookup refreshed = redis.lookup(key);
        T filledByOther = readRedis(key, type, hot, refreshed, waitRevision);
        if (filledByOther != null) {
          return filledByOther;
        }
        if (System.nanoTime() >= deadline) {
          throw busy();
        }
        sleepOrThrowBusy(
            ThreadLocalRandom.current()
                .nextLong(DETAIL_RETRY_MIN_MILLIS, DETAIL_RETRY_MAX_MILLIS_EXCLUSIVE));
      }

      // 拿到锁后再检查一次：另一个实例可能恰好在我们抢锁之前回填完成。
      // 这次读取拿到的回填凭据（ticket）也是后面写回 Redis 时要用的。
      long revisionBeforeLoad = revision.get();
      long readRevision = revision.get();
      RedisCatalogStore.Lookup found = redis.lookup(key);
      T hit = readRedis(key, type, hot, found, readRevision);
      if (hit != null) {
        metrics.increment("rebuild_avoided_after_redis_lock");
        return hit;
      }

      T value = loadFromDatabase(loader);
      storeLoadedValue(key, value, ttl, hot, found, token, revisionBeforeLoad);
      return value;
    } finally {
      redis.releaseLoad(key, token);
    }
  }

  /** 占用一个回源额度后调用 loader；额度用尽时直接返回 503，不排队。 */
  private <T> T loadFromDatabase(Supplier<T> loader) {
    if (!loads.tryAcquire()) {
      throw busy();
    }
    try {
      metrics.increment("database_load");
      return loader.get();
    } finally {
      loads.release();
    }
  }

  /**
   * 把数据库结果写回 Redis，热点数据再写本地缓存。
   *
   * <p>"商品不存在"也会缓存，但使用较短的空值 TTL。只有满足以下条件才写 Redis：序列化成功、拿到了回填凭据、期间没有发生本地失效。
   * fillOwned 还会在 Redis 内校验凭据和锁所有权，失败说明期间被删除或已失去锁，此时放弃回填。
   */
  private void storeLoadedValue(
      String key,
      Object value,
      Duration ttl,
      boolean hot,
      RedisCatalogStore.Lookup found,
      String token,
      long revisionBeforeLoad) {
    boolean negative = value instanceof DetailValue detail && detail.getProduct() == null;
    if (negative) {
      metrics.increment("negative_load");
    }
    String encoded = encode(value);
    Duration effectiveTtl = ttls.jitter(negative ? properties.getNegativeTtl() : ttl);

    boolean canFill = encoded != null && found != null && revisionBeforeLoad == revision.get();
    if (!canFill) {
      return;
    }
    boolean stored = redis.fillOwned(key, found.getTicket(), encoded, effectiveTtl, token);
    if (stored) {
      metrics.increment("rebuild_filled");
      if (hot && !negative && revisionBeforeLoad == revision.get()) {
        remember(key, revisionBeforeLoad, encoded);
      }
    } else {
      metrics.increment("fill_skipped");
    }
  }

  /**
   * 解析 Redis 读取结果，未命中或内容损坏时返回 null。
   *
   * <p>损坏的值会被删除（只删这个值本身，见 RedisCatalogStore.discard）。命中的空商品不提升到本地缓存，让它随短 TTL 自然过期。
   */
  private <T> T readRedis(
      String key,
      Class<T> type,
      boolean hot,
      RedisCatalogStore.Lookup found,
      long readRevision) {
    if (found == null || found.getValue() == null) {
      return null;
    }
    T value = decode(found.getValue(), type);
    if (value == null) {
      redis.discard(key, found.getValue());
      metrics.increment("corrupt_entry");
      return null;
    }
    metrics.increment("redis_hit");
    boolean negative = value instanceof DetailValue detail && detail.getProduct() == null;
    if (negative) {
      metrics.increment("negative_hit");
    } else if (hot && readRevision == revision.get() && redis.available()) {
      remember(key, readRevision, found.getValue());
    }
    return value;
  }

  /**
   * 读取本地缓存。
   *
   * <p>本地缓存依赖 Redis 广播来接收其他实例的失效通知。key 不再是热点或 Redis 不可用时，可能已经错过失效通知，所以直接丢弃本地值。
   */
  private <T> T readLocal(String key, Class<T> type, boolean hot) {
    if (!hot || !redis.available()) {
      local.invalidate(key);
      return null;
    }
    String cached = local.getIfPresent(key);
    if (cached == null) {
      return null;
    }
    T decoded = decode(cached, type);
    if (decoded == null) {
      local.invalidate(key);
    } else {
      metrics.increment("local_hit");
    }
    return decoded;
  }

  /** 序列化为 JSON；失败时返回 null 表示跳过缓存，不影响本次查询结果。 */
  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      metrics.increment("codec_error");
      return null;
    }
  }

  /** 按指定类型反序列化；内容损坏时返回 null，由调用方当作未命中处理。 */
  private <T> T decode(String value, Class<T> type) {
    try {
      JavaType target = json.constructType(type);
      return json.readValue(value, target);
    } catch (Exception ex) {
      return null;
    }
  }

  /** 等锁超时或回源额度用尽时返回 503，让请求明确失败而不是无限堆积。 */
  private ResponseStatusException busy() {
    metrics.increment("load_rejected");
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品查询繁忙，请稍后重试");
  }

  /** 休眠后重试；线程被中断时恢复中断标记并按繁忙失败。 */
  private void sleepOrThrowBusy(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw busy();
    }
  }

  /** 事务提交后删除库存展示的 Redis 缓存。库存从不进本地缓存，所以不广播、不清本地。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void committed(CatalogChanged change) {
    redis.invalidate(change.stockKeys(), false);
    metrics.increment("committed_invalidation");
  }

  /**
   * 写入本地缓存。
   *
   * <p>与 invalidateLocal 用同一把对象锁串行执行：保证"检查修订号未变 + 写入"不会和"修订号加 1 + 删除"交错，否则晚到的查询可能把旧值写回。
   */
  private synchronized void remember(String key, long expectedRevision, String data) {
    if (revision.get() == expectedRevision) {
      local.put(key, data);
    }
  }

  public synchronized void invalidateLocal(String key) {
    revision.incrementAndGet();
    local.invalidate(key);
    metrics.increment("local_invalidation");
  }

  public synchronized void invalidateLocal() {
    revision.incrementAndGet();
    local.invalidateAll();
    metrics.increment("local_invalidation");
  }

  /**
   * SKU 报价批量回源：同一商品的所有 SKU 共用一把锁，并与详情查询共用回源额度。
   *
   * <p>这里只负责拿锁和限流，具体的读取和回填由调用方在 load 中完成：调用方拿到锁后应重读 Redis，以获得新的回填凭据。
   *
   * @param shared 是否使用跨实例锁；为 false 时（缓存不可用）只做本机限流
   * @param load 参数为锁持有者标识，未拿到锁时为 null
   */
  public <T> T loadOffers(long productId, boolean shared, Function<String, T> load) {
    String lockKey = OFFERS_KEY_PREFIX + productId;
    String owner = null;
    try {
      if (shared) {
        owner = waitForOffersLock(lockKey);
      }
      if (!loads.tryAcquire()) {
        throw busy();
      }
      try {
        return load.apply(owner);
      } finally {
        loads.release();
      }
    } finally {
      redis.releaseLoad(lockKey, owner);
    }
  }

  /** 循环抢报价锁直到成功或超时。返回锁持有者标识；Redis 故障时返回 null，改走本机限流回源。 */
  private String waitForOffersLock(String lockKey) {
    long deadline = System.nanoTime() + protection.getLockWait().toNanos();
    while (true) {
      Boolean acquired = redis.acquireLoad(lockKey);
      if (acquired == null) {
        return null;
      }
      if (acquired) {
        return redis.ownerToken();
      }
      if (System.nanoTime() >= deadline) {
        throw busy();
      }
      sleepOrThrowBusy(OFFERS_RETRY_MILLIS);
    }
  }

  /** 汇总缓存配置、Redis 与热点探测状态以及各项计数，供运维端点查看。 */
  public Map<String, Object> status() {
    return Map.of(
        "localMode", localPolicy.getMode(),
        "bloom", bloom == null ? Map.of("enabled", false) : bloom.status(),
        "ttlJitterRatio", protection.getTtlJitterRatio(),
        "redis", redis.status(),
        "hotkey", hotKeys.status(),
        "counters", metrics.snapshot(),
        "localEntries", local.estimatedSize(),
        "localTtlMillis", properties.getLocalTtl().toMillis(),
        "detailTtlMillis", properties.getDetailTtl().toMillis(),
        "searchTtlMillis", properties.getSearchTtl().toMillis());
  }

  /**
   * 商品详情的缓存包装。
   *
   * <p>product 为 null 表示"商品不存在"。包一层是为了让"不存在"也能被序列化并短暂缓存，避免反复查询不存在的商品压到数据库。
   */
  public static final class DetailValue {
    /** 商品稳定资料；不含 SKU 价格和库存，避免把过期的交易数据写进缓存。 */
    private final ProductProfile product;

    @JsonCreator
    public DetailValue(@JsonProperty("product") ProductProfile product) {
      this.product = product;
    }

    /** 返回商品资料；为 null 表示商品不存在。 */
    public ProductProfile getProduct() {
      return product;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      DetailValue that = (DetailValue) other;
      return Objects.equals(product, that.product);
    }

    @Override
    public int hashCode() {
      return Objects.hash(product);
    }

    @Override
    public String toString() {
      return "DetailValue[" + "product=" + product + "]";
    }
  }
}
