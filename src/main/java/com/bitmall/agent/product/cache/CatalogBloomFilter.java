package com.bitmall.agent.product.cache;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.config.CatalogProtectionProperties;
import com.bitmall.agent.product.dao.mapper.ProductMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 商品 ID 存在性布隆过滤器，基于 Redisson 原生 RBloomFilter。
 *
 * <p>用途：商品详情缓存未命中时，先判断这个商品 ID 是否"可能存在"，明确不存在的请求直接返回空，不再打到 Redis 详情缓存和数据库。
 *
 * <p>设计约束：
 *
 * <ul>
 *   <li>只追加商品 ID，从不删除已写入的位。下架或回滚的商品最多造成误判（多回源一次），不会造成漏判。
 *   <li>过滤器状态未知（未构建完、构建中、Redis 故障、位图被清掉）时一律放行，由后续有界回源兜底。
 *   <li>元数据哈希里的 ready / generation 用来识别"检查期间过滤器被重建或丢失"，此时同样放行。
 * </ul>
 */
@Component
@DependsOnDatabaseInitialization
public class CatalogBloomFilter {
  /** 元数据哈希中的字段名。ready=1 表示过滤器已完整构建并可用于拒绝请求。 */
  private static final String READY_FIELD = "ready";
  private static final String GENERATION_FIELD = "generation";
  private static final String EXPECTED_PRODUCTS_FIELD = "expectedProducts";
  private static final String FALSE_PROBABILITY_FIELD = "falseProbability";
  private static final String READY = "1";
  private static final String NOT_READY = "0";

  private static final String METRIC_REJECTED = "bloom_rejected";
  private static final String METRIC_BYPASSED = "bloom_bypassed";
  private static final String METRIC_REBUILT = "bloom_rebuilt";
  private static final String METRIC_BUILD_FAILED = "bloom_build_failed";

  /**
   * 发布"构建完成"的 Lua 脚本。
   *
   * <p>KEYS[1]=构建锁，KEYS[2]=元数据，KEYS[3]=位图；ARGV[1]=锁持有者。只有当前线程仍持有构建锁、且位图仍存在时，才把 ready 置为 1
   * 并写入新的 generation 等元数据。这样暂停后失去锁的旧构建线程不能把一个残缺的过滤器宣布为可用。
   */
  private static final String PUBLISH_READY_SCRIPT =
      "if redis.call('HEXISTS',KEYS[1],ARGV[1])~=1 or redis.call('EXISTS',KEYS[3])~=1 then"
          + " return 0 end; "
          + "redis.call('HSET',KEYS[2],'ready','1','generation',ARGV[2],'count',ARGV[3],'expectedProducts',ARGV[4],'falseProbability',ARGV[5]);return"
          + " 1";

  /** 读取商品缓存总开关和 Redis 命名空间（用于拼过滤器 key）。 */
  private final CatalogCacheProperties cache;
  /** 读取布隆过滤器开关、预期商品数、误判率、重建锁等待时间和扫描批大小。 */
  private final CatalogProtectionProperties properties;
  /** 取得商品缓存的 Redisson 客户端，并共用它的熔断状态和锁持有者标识。 */
  private final RedisCatalogStore store;
  /** 重建过滤器时分批扫描数据库里的全部商品 ID。 */
  private final ProductMapper products;
  /** 记录过滤器拒绝、降级放行，以及重建成功和失败的次数。 */
  private final CacheMetrics metrics;

  private final String filterKey;
  private final String metadataKey;

  /** 构建和发布前登记共用的分布式锁，保证两者不会交错执行。 */
  private final String buildLockKey;

  /** 已登记但所在 SQL 事务尚未结束的商品 ID，键为一次登记的随机 token。 */
  private final String pendingKey;

  /** 本机防重入标记：同一实例上同时只跑一次重建。 */
  private final AtomicBoolean building = new AtomicBoolean();

  /** 延迟创建的 Redisson 过滤器句柄，用双重检查锁保证只创建一次。 */
  private volatile RBloomFilter<Long> filter;

  public CatalogBloomFilter(
      CatalogCacheProperties cache,
      CatalogProtectionProperties properties,
      RedisCatalogStore store,
      ProductMapper products,
      CacheMetrics metrics) {
    this.cache = cache;
    this.properties = properties;
    this.store = store;
    this.products = products;
    this.metrics = metrics;
    String prefix = cache.getNamespace() + ":{catalog}:bloom:redisson:v2:";
    filterKey = prefix + "active";
    metadataKey = prefix + "metadata";
    buildLockKey = prefix + "build-lock";
    pendingKey = prefix + "pending-publications";
  }

  private boolean enabled() {
    return cache.isEnabled() && properties.isBloomEnabled();
  }

  public String filterKey() {
    return filterKey;
  }

  public String metadataKey() {
    return metadataKey;
  }

  private RMap<String, String> metadata() {
    return store.client().getMap(metadataKey, StringCodec.INSTANCE);
  }

  private RMap<String, String> pending() {
    return store.client().getMap(pendingKey, StringCodec.INSTANCE);
  }

  private RBloomFilter<Long> filter() {
    if (filter == null) {
      synchronized (this) {
        if (filter == null) {
          filter = store.client().getBloomFilter(filterKey, LongCodec.INSTANCE);
        }
      }
    }
    return filter;
  }

  /**
   * 确保 Redis 中的过滤器已按当前配置初始化。
   *
   * <p>tryInit 是幂等的：已初始化时不会覆盖原参数。所以如果配置里的容量或误判率改了，旧位图会仍按旧参数存在；这时必须报错，
   * 不能用新参数去解释旧位图，否则判断结果没有意义。修改参数需要换命名空间重建。
   */
  private RBloomFilter<Long> initialize() {
    RBloomFilter<Long> bloom = filter();
    bloom.tryInit(properties.getExpectedProducts(), properties.getFalsePositiveRate());
    boolean capacityMatches = bloom.getExpectedInsertions() == properties.getExpectedProducts();
    boolean falseRateMatches =
        Double.compare(bloom.getFalseProbability(), properties.getFalsePositiveRate()) == 0;
    if (!capacityMatches || !falseRateMatches) {
      throw new IllegalStateException("布隆容量参数已变化，请使用新命名空间重建");
    }
    return bloom;
  }

  /**
   * 判断商品 ID 是否可能存在。只在展示缓存未命中时调用。
   *
   * <p>返回 false 表示"确定不存在"，调用方会直接返回空商品，所以只有在过滤器确定可信时才能返回 false：检查前后都读取一次元数据，
   * 如果期间过滤器未就绪、位图丢失或 generation 变化（被重建过），都放行返回 true。Redis 异常同样放行。
   */
  public boolean mightContain(long productId) {
    if (!enabled() || !store.available()) {
      return true;
    }
    try {
      Map<String, String> before = metadata().readAllMap();
      if (!isReadyForCurrentConfig(before)) {
        return bypass();
      }
      boolean exists = filter().contains(productId);
      Map<String, String> after = metadata().readAllMap();
      if (!isUnchangedSince(before, after)) {
        return bypass();
      }
      if (!exists) {
        metrics.increment(METRIC_REJECTED);
      }
      return exists;
    } catch (RuntimeException ex) {
      store.failure(ex);
      return bypass();
    }
  }

  /** 检查前的元数据：已就绪、有 generation、位图仍在，且构建时用的参数与当前配置一致。按顺序短路判断，避免多余的 Redis 访问。 */
  private boolean isReadyForCurrentConfig(Map<String, String> metadata) {
    if (!READY.equals(metadata.get(READY_FIELD))) {
      return false;
    }
    if (metadata.get(GENERATION_FIELD) == null) {
      return false;
    }
    if (!dataExists()) {
      return false;
    }
    String expectedProducts = String.valueOf(properties.getExpectedProducts());
    if (!expectedProducts.equals(metadata.get(EXPECTED_PRODUCTS_FIELD))) {
      return false;
    }
    String falseProbability = String.valueOf(properties.getFalsePositiveRate());
    return falseProbability.equals(metadata.get(FALSE_PROBABILITY_FIELD));
  }

  /** 检查后的元数据：位图仍在、仍就绪，且 generation 没变（即检查期间没有发生重建）。 */
  private boolean isUnchangedSince(Map<String, String> before, Map<String, String> after) {
    if (!dataExists()) {
      return false;
    }
    if (!READY.equals(after.get(READY_FIELD))) {
      return false;
    }
    return Objects.equals(before.get(GENERATION_FIELD), after.get(GENERATION_FIELD));
  }

  /**
   * 判断位图键本身是否存在。
   *
   * <p>不能用 RBloomFilter.isExists：它会同时检查 Redisson 的配置键，配置键还在但位图被清掉时也会返回 true。
   */
  private boolean dataExists() {
    return store.client().getBucket(filterKey, StringCodec.INSTANCE).isExists();
  }

  /** 过滤器不可信时放行请求，并计数。 */
  private boolean bypass() {
    metrics.increment(METRIC_BYPASSED);
    return true;
  }

  /**
   * 发布商品前把它的 ID 登记进过滤器。必须在发布商品的 SQL 事务内调用。
   *
   * <p>为什么在提交前登记：如果等提交后再加，提交到加位之间的请求会被误判为不存在。提前登记的代价是事务回滚后该 ID 留在过滤器里，
   * 只会造成一次误判回源，可以接受。
   *
   * <p>登记同时写入 pending 表：如果在事务结束前位图丢失并被重建，重建会把 pending 中的 ID 一并补回，避免漏掉尚未提交的商品。
   * 事务结束后（无论提交还是回滚）再补一次位并移除 pending 记录。
   *
   * <p>任何失败都抛异常，让发布事务回滚，而不是发布一个过滤器里没有的商品。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void registerBeforePublish(long productId) {
    if (!enabled()) {
      return;
    }
    String token = UUID.randomUUID().toString();
    RLock lock = store.client().getLock(buildLockKey);
    boolean owned = false;
    try {
      owned = lock.tryLock(properties.getLockWait().toMillis(), TimeUnit.MILLISECONDS);
      if (!owned) {
        throw new IllegalStateException("商品存在性正在构建，请稍后重试发布");
      }
      pending().put(token, String.valueOf(productId));
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              completeRegistration(token, productId);
            }
          });
      addRegistered(productId);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("商品存在性登记被中断", ex);
    } catch (RuntimeException ex) {
      store.failure(ex);
      throw new IllegalStateException("商品存在性登记失败，本次发布回滚", ex);
    } finally {
      if (owned) {
        unlockQuietly(lock);
      }
    }
  }

  /** 把 ID 加入位图。如果位图已经丢失，先把 ready 置 0，使过滤器在下一次重建完成前不再拒绝任何请求。 */
  private void addRegistered(long productId) {
    RBloomFilter<Long> bloom = initialize();
    if (!dataExists()) {
      metadata().put(READY_FIELD, NOT_READY);
    }
    bloom.add(productId);
  }

  /**
   * SQL 事务结束（提交或回滚）后的收尾：再补一次位，然后移除 pending 记录。
   *
   * <p>这里已在事务之外，不能再影响发布结果，所以拿不到锁或失败都静默返回；pending 记录会留下，由下次重建补位。
   */
  private void completeRegistration(String token, long productId) {
    RLock lock = store.client().getLock(buildLockKey);
    boolean owned = false;
    try {
      owned = lock.tryLock(properties.getLockWait().toMillis(), TimeUnit.MILLISECONDS);
      if (!owned) {
        return;
      }
      addRegistered(productId);
      pending().remove(token);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException ex) {
      store.failure(ex);
    } finally {
      if (owned) {
        unlockQuietly(lock);
      }
    }
  }

  @Async("catalogMaintenanceExecutor")
  @Scheduled(
      fixedDelayString = "${app.catalog-protection.bloom-rebuild-interval-ms:300000}",
      initialDelay = 500)
  public void refresh() {
    if (enabled()) {
      rebuild();
    }
  }

  /**
   * 从数据库补全过滤器并重新发布为可用。
   *
   * <p>这是增量补全而不是清空重建：清空会丢掉其他实例刚登记、但数据库还没提交的商品 ID。步骤为：
   *
   * <ol>
   *   <li>拿构建锁（不等待，已有实例在构建就直接返回），先把 ready 置 0；
   *   <li>分批扫描数据库所有商品 ID 写入位图，再补入 pending 中尚未完成的登记；
   *   <li>重新扫描一遍校验所有 ID 都在位图里，防止构建期间位图被清掉；
   *   <li>用 Lua 脚本在仍持有锁的前提下发布 ready=1 和新的 generation。
   * </ol>
   *
   * @return 本次是否成功发布
   */
  public boolean rebuild() {
    if (!enabled() || !store.available()) {
      return false;
    }
    if (!building.compareAndSet(false, true)) {
      return false;
    }
    RLock lock = null;
    boolean owned = false;
    try {
      lock = store.client().getLock(buildLockKey);
      owned = lock.tryLock(0, TimeUnit.MILLISECONDS);
      if (!owned) {
        return false;
      }
      metadata().put(READY_FIELD, NOT_READY);
      RBloomFilter<Long> bloom = initialize();

      long count = addAllProductIds(bloom, lock);
      List<Long> registered = addPendingRegistrations(bloom);
      verifyAllProductIdsPresent(bloom);
      verifyRegistrationsPresent(bloom, registered);
      publishReady(count);

      metrics.increment(METRIC_REBUILT);
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (RuntimeException ex) {
      store.failure(ex);
      metrics.increment(METRIC_BUILD_FAILED);
      return false;
    } finally {
      if (owned && lock != null) {
        unlockQuietly(lock);
      }
      building.set(false);
    }
  }

  /** 按 ID 游标分批扫描数据库，把所有商品 ID 写入位图；每批写入前确认构建锁还在。返回写入的 ID 总数。 */
  private long addAllProductIds(RBloomFilter<Long> bloom, RLock lock) {
    long cursor = 0;
    long count = 0;
    while (true) {
      List<Long> ids = selectIdsAfter(cursor);
      if (ids.isEmpty()) {
        break;
      }
      if (!lock.isHeldByCurrentThread()) {
        throw new IllegalStateException("布隆构建执行权已失效");
      }
      bloom.add(ids);
      cursor = ids.get(ids.size() - 1);
      count += ids.size();
    }
    return count;
  }

  /** 把 pending 中尚未完成的发布登记补入位图，返回这些 ID 供后续校验。 */
  private List<Long> addPendingRegistrations(RBloomFilter<Long> bloom) {
    List<Long> registered = pending().readAllValues().stream().map(Long::valueOf).toList();
    if (!registered.isEmpty()) {
      bloom.add(registered);
    }
    return registered;
  }

  /** 再扫描一遍数据库，确认每个 ID 都在位图中；位图在构建期间被清掉时抛异常，保持未就绪等待下次补全。 */
  private void verifyAllProductIdsPresent(RBloomFilter<Long> bloom) {
    long cursor = 0;
    while (true) {
      List<Long> ids = selectIdsAfter(cursor);
      if (ids.isEmpty()) {
        break;
      }
      boolean allPresent = dataExists() && bloom.contains(ids) == ids.size();
      if (!allPresent) {
        throw new IllegalStateException("布隆构建期间位图丢失，保持未就绪并等待补全");
      }
      cursor = ids.get(ids.size() - 1);
    }
  }

  private void verifyRegistrationsPresent(RBloomFilter<Long> bloom, List<Long> registered) {
    if (!registered.isEmpty() && bloom.contains(registered) != registered.size()) {
      throw new IllegalStateException("布隆待发布登记尚未补齐");
    }
  }

  /** 原子地校验锁所有权并发布 ready 元数据，失败说明已失去构建权或位图丢失。 */
  private void publishReady(long count) {
    Long published =
        RedissonSupport.eval(
            store.client(),
            PUBLISH_READY_SCRIPT,
            RScript.ReturnType.LONG,
            List.of(buildLockKey, metadataKey, filterKey),
            store.ownerToken(),
            UUID.randomUUID().toString(),
            String.valueOf(count),
            String.valueOf(properties.getExpectedProducts()),
            String.valueOf(properties.getFalsePositiveRate()));
    if (!Long.valueOf(1).equals(published)) {
      throw new IllegalStateException("布隆构建发布失败");
    }
  }

  private List<Long> selectIdsAfter(long cursor) {
    return products.selectBloomIds(Map.of("cursor", cursor, "limit", properties.getScanBatch()));
  }

  /** 只释放当前线程仍持有的锁；解锁失败只记录 Redis 故障，不覆盖原本的返回值或异常。 */
  private void unlockQuietly(RLock lock) {
    try {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    } catch (RuntimeException ex) {
      store.failure(ex);
    }
  }

  public Map<String, Object> status() {
    return Map.of(
        "enabled", enabled(),
        "implementation", "Redisson RBloomFilter",
        "expectedProducts", properties.getExpectedProducts(),
        "targetFalsePositiveRate", properties.getFalsePositiveRate(),
        "building", building.get(),
        "filterKey", filterKey);
  }
}
