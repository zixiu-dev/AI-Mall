package com.bitmall.agent.product.cache;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.product.config.*;
import com.bitmall.agent.product.dao.mapper.ProductMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 原生 Redisson RBloomFilter。只追加商品 ID，不自算哈希、不清除已登记位；未知与丢键时放行到有界回源。 */
@Component
@DependsOnDatabaseInitialization
public class CatalogBloomFilter {
  private final CatalogCacheProperties cache;
  private final CatalogProtectionProperties properties;
  private final RedisCatalogStore store;
  private final ProductMapper products;
  private final CacheMetrics metrics;
  private final String filterKey;
  private final String metadataKey;
  private final String buildLockKey;
  private final String pendingKey;
  private final AtomicBoolean building = new AtomicBoolean();
  private volatile RBloomFilter<Long> filter;

  public CatalogBloomFilter(CatalogCacheProperties cache, CatalogProtectionProperties properties,
      RedisCatalogStore store, ProductMapper products, CacheMetrics metrics) {
    this.cache = cache; this.properties = properties; this.store = store;
    this.products = products; this.metrics = metrics;
    String prefix = cache.getNamespace() + ":{catalog}:bloom:redisson:v2:";
    filterKey = prefix + "active";
    metadataKey = prefix + "metadata";
    buildLockKey = prefix + "build-lock";
    pendingKey = prefix + "pending-publications";
  }

  private boolean enabled() { return cache.isEnabled() && properties.isBloomEnabled(); }
  public String filterKey() { return filterKey; }
  public String metadataKey() { return metadataKey; }
  private RMap<String,String> metadata() {
    return store.client().getMap(metadataKey, StringCodec.INSTANCE);
  }
  private RBloomFilter<Long> filter() {
    if (filter == null) synchronized (this) {
      if (filter == null) filter = store.client().getBloomFilter(filterKey, LongCodec.INSTANCE);
    }
    return filter;
  }

  /** tryInit 是幂等初始化；容量参数变更不能把旧位图按另一套哈希参数解释。 */
  private RBloomFilter<Long> initialize() {
    var bloom = filter();
    bloom.tryInit(properties.getExpectedProducts(), properties.getFalsePositiveRate());
    if (bloom.getExpectedInsertions() != properties.getExpectedProducts()
        || Double.compare(bloom.getFalseProbability(), properties.getFalsePositiveRate()) != 0)
      throw new IllegalStateException("布隆容量参数已变化，请使用新命名空间重建");
    return bloom;
  }

  /** 只在展示缓存未命中时调用；检查期间数据丢失或就绪版本变化，都不能当作明确不存在。 */
  public boolean mightContain(long productId) {
    if (!enabled() || !store.available()) return true;
    try {
      var before = metadata().readAllMap();
      if (!"1".equals(before.get("ready")) || before.get("generation") == null || !dataExists()
          || !String.valueOf(properties.getExpectedProducts()).equals(before.get("expectedProducts"))
          || !String.valueOf(properties.getFalsePositiveRate()).equals(before.get("falseProbability")))
        return bypass();
      boolean exists = filter().contains(productId);
      var after = metadata().readAllMap();
      if (!dataExists() || !"1".equals(after.get("ready"))
          || !Objects.equals(before.get("generation"), after.get("generation"))) return bypass();
      if (!exists) metrics.increment("bloom_rejected");
      return exists;
    } catch (RuntimeException ex) { store.failure(ex); return bypass(); }
  }

  /** RBloomFilter.isExists 会合并检查配置键，不能用它判断原始位图是否丢失。 */
  private boolean dataExists() {
    return store.client().getBucket(filterKey, StringCodec.INSTANCE).isExists();
  }

  private boolean bypass() { metrics.increment("bloom_bypassed"); return true; }

  /** 发布前登记必须在 SQL 事务内调用。单调追加保留尚未提交/回滚的登记，后者至多造成误判。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void registerBeforePublish(long productId) {
    if (!enabled()) return;
    String token = UUID.randomUUID().toString();
    RLock lock = store.client().getLock(buildLockKey);
    boolean owned = false;
    try {
      owned = lock.tryLock(properties.getLockWait().toMillis(),TimeUnit.MILLISECONDS);
      if (!owned) throw new IllegalStateException("商品存在性正在构建，请稍后重试发布");
      // 只保留尚未完成 SQL 提交回调的登记；构建时一并补入，避免丢位后遗漏未提交商品。
      pending().put(token,String.valueOf(productId));
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { completeRegistration(token,productId); }
      });
      addRegistered(productId);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("商品存在性登记被中断",ex);
    } catch (RuntimeException ex) {
      store.failure(ex); throw new IllegalStateException("商品存在性登记失败，本次发布回滚",ex);
    } finally {
      if (owned) try { if (lock.isHeldByCurrentThread()) lock.unlock(); }
      catch (RuntimeException ex) { store.failure(ex); }
    }
  }

  private RMap<String,String> pending() {
    return store.client().getMap(pendingKey,StringCodec.INSTANCE);
  }

  private void addRegistered(long productId) {
    var bloom = initialize();
    if (!dataExists()) metadata().put("ready","0");
    bloom.add(productId);
  }

  /** SQL 提交/回滚后再补一次位才移除登记，失败留下待登记 ID；回滚最多产生误判，不删除共享位。 */
  private void completeRegistration(String token,long id) {
    RLock lock = store.client().getLock(buildLockKey);
    boolean owned = false;
    try {
      owned = lock.tryLock(properties.getLockWait().toMillis(),TimeUnit.MILLISECONDS);
      if (!owned) return;
      addRegistered(id);
      pending().remove(token);
    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    catch (RuntimeException ex) { store.failure(ex); }
    finally {
      if (owned) try { if (lock.isHeldByCurrentThread()) lock.unlock(); }
      catch (RuntimeException ex) { store.failure(ex); }
    }
  }

  @Async("catalogMaintenanceExecutor")
  @Scheduled(fixedDelayString = "${app.catalog-protection.bloom-rebuild-interval-ms:300000}", initialDelay = 500)
  public void refresh() { if (enabled()) rebuild(); }

  /** 增量扫描补全而非清空重建：不丢掉其他实例在数据库提交之前已登记的商品 ID。 */
  public boolean rebuild() {
    if (!enabled() || !store.available() || !building.compareAndSet(false,true)) return false;
    RLock lock = null; boolean owned = false;
    try {
      lock = store.client().getLock(buildLockKey);
      owned = lock.tryLock(0, TimeUnit.MILLISECONDS);
      if (!owned) return false;
      metadata().put("ready", "0");
      var bloom = initialize();
      long cursor = 0, count = 0;
      while (true) {
        var ids = products.selectBloomIds(Map.of("cursor",cursor,"limit",properties.getScanBatch()));
        if (ids.isEmpty()) break;
        if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("布隆构建执行权已失效");
        bloom.add(ids);
        cursor = ids.get(ids.size()-1); count += ids.size();
      }
      var registered = pending().readAllValues().stream().map(Long::valueOf).toList();
      if (!registered.isEmpty()) bloom.add(registered);
      long verifiedCursor = 0;
      while (true) {
        var ids = products.selectBloomIds(Map.of("cursor",verifiedCursor,"limit",properties.getScanBatch()));
        if (ids.isEmpty()) break;
        if (!dataExists() || bloom.contains(ids) != ids.size())
          throw new IllegalStateException("布隆构建期间位图丢失，保持未就绪并等待补全");
        verifiedCursor = ids.get(ids.size()-1);
      }
      if (!registered.isEmpty() && bloom.contains(registered) != registered.size())
        throw new IllegalStateException("布隆待发布登记尚未补齐");
      // 与锁所有权一起发布 ready；失去构建权的旧线程不得把残缺过滤器宣布为可用。
      String publish = "if redis.call('HEXISTS',KEYS[1],ARGV[1])~=1 or redis.call('EXISTS',KEYS[3])~=1 then return 0 end; "
          + "redis.call('HSET',KEYS[2],'ready','1','generation',ARGV[2],'count',ARGV[3],"
          + "'expectedProducts',ARGV[4],'falseProbability',ARGV[5]);return 1";
      Long done = RedissonSupport.eval(store.client(),publish,RScript.ReturnType.LONG,
          List.of(buildLockKey,metadataKey,filterKey),store.ownerToken(),UUID.randomUUID().toString(),
          String.valueOf(count),String.valueOf(properties.getExpectedProducts()),String.valueOf(properties.getFalsePositiveRate()));
      if (!Long.valueOf(1).equals(done)) throw new IllegalStateException("布隆构建发布失败");
      metrics.increment("bloom_rebuilt"); return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt(); return false;
    } catch (RuntimeException ex) {
      store.failure(ex); metrics.increment("bloom_build_failed"); return false;
    } finally {
      if (owned && lock != null) try { if (lock.isHeldByCurrentThread()) lock.unlock(); }
      catch (RuntimeException ex) { store.failure(ex); }
      building.set(false);
    }
  }

  public Map<String,Object> status() {
    return Map.of("enabled",enabled(),"implementation","Redisson RBloomFilter",
        "expectedProducts",properties.getExpectedProducts(),"targetFalsePositiveRate",properties.getFalsePositiveRate(),
        "building",building.get(),"filterKey",filterKey);
  }
}
