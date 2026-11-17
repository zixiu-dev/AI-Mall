package com.bitmall.agent.product.cache;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Redisson 商品缓存适配。普通字符串、RLock、RTopic 共用缓存连接，脚本保留代际与所有者的原子检查。 */
@Component
public class RedisCatalogStore {
  private static final String GENERATION = "local g=redis.call('GET',KEYS[1]); if g then return g end; redis.call('SET',KEYS[1],ARGV[1]);return ARGV[1]";
  private static final String FILL = "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end; redis.call('PSETEX',KEYS[2],ARGV[2],ARGV[3]);return 1";
  // RLock 的 Redis Hash 所有者为 clientId:threadId，和 Redisson 4.6.1 的所有权格式一致。
  private static final String OWNED_FILL = "if redis.call('GET',KEYS[1])~=ARGV[1] or redis.call('HEXISTS',KEYS[3],ARGV[4])~=1 then return 0 end; redis.call('PSETEX',KEYS[2],ARGV[2],ARGV[3]);return 1";
  private static final String INVALIDATE = "redis.call('SET',KEYS[1],ARGV[1]);redis.call('PUBLISH',ARGV[2],ARGV[1]);return 1";
  private final ObjectProvider<RedissonClient> clients;
  private final CatalogCacheProperties properties;
  private final CacheMetrics metrics;
  private final AtomicLong retryAt = new AtomicLong();

  public RedisCatalogStore(@Qualifier("catalogRedisson") ObjectProvider<RedissonClient> clients,
      CatalogCacheProperties properties, CacheMetrics metrics) {
    this.clients = clients;
    this.properties = properties;
    this.metrics = metrics;
  }

  /** 按需取得 Redisson，冷却期间不因普通读取反复连接故障 Redis。 */
  public RedissonClient client() { return clients.getObject(); }
  public String generationKey() { return properties.getNamespace() + ":{catalog}:generation"; }
  public String channel() { return properties.getNamespace() + ":invalidate"; }
  private String key(String generation, String logical) {
    return properties.getNamespace() + ":{catalog}:" + generation + ":" + logical;
  }
  /** 新锁使用独立版本键，避免与旧 SET NX 字符串锁产生 Redis 类型冲突。 */
  public String lockKey(String logical) {
    return properties.getNamespace() + ":{catalog}:load-lock:redisson:v2:" + logical;
  }
  public boolean available() {
    return properties.isEnabled() && System.nanoTime() >= retryAt.get();
  }

  /** 查询命中不取锁；回填仍以本次看到的代际为条件。 */
  public Lookup lookup(String logical) {
    if (!available()) return null;
    try {
      String generation = RedissonSupport.eval(client(), GENERATION, RScript.ReturnType.VALUE,
          List.of(generationKey()), UUID.randomUUID().toString());
      if (generation == null) throw new IllegalStateException("缺少缓存代际");
      return new Lookup(generation, client().<String>getBucket(key(generation, logical), StringCodec.INSTANCE).get());
    } catch (RuntimeException ex) { failure(ex); return null; }
  }

  /** 无业务重建的测试/维护回填入口；生产回源使用 fillOwned。 */
  public boolean fill(String logical, String generation, String value, Duration ttl) {
    if (!available() || generation == null) return false;
    try {
      return Long.valueOf(1).equals(RedissonSupport.eval(client(), FILL, RScript.ReturnType.LONG,
          List.of(generationKey(), key(generation, logical)), generation, String.valueOf(ttl.toMillis()), value));
    } catch (RuntimeException ex) { failure(ex); return false; }
  }

  /** true 已持有跨实例锁；false 别人正在重建；null 基础设施故障，改走受限回源。未指定固定租约，使用看门狗。 */
  public Boolean acquireLoad(String logical) {
    if (!available()) return null;
    try { return client().getLock(lockKey(logical)).tryLock(); }
    catch (RuntimeException ex) { failure(ex); return null; }
  }

  /** 与 Redisson RLock 一致的所有者名，回填脚本需要在 Redis 内核对它。 */
  public String ownerToken() { return client().getId() + ":" + Thread.currentThread().getId(); }

  /** 代际与 RLock 所有权同时成立才写缓存，暂停后失去锁的旧线程不能污染新结果。 */
  public boolean fillOwned(String logical, String generation, String value, Duration ttl, String owner) {
    if (!available() || generation == null || owner == null) return false;
    try {
      return Long.valueOf(1).equals(RedissonSupport.eval(client(), OWNED_FILL, RScript.ReturnType.LONG,
          List.of(generationKey(), key(generation, logical), lockKey(logical)),
          generation, String.valueOf(ttl.toMillis()), value, owner));
    } catch (RuntimeException ex) { failure(ex); return false; }
  }

  /** 仅解开当前线程仍持有的锁；正常完成停止看门狗，异常失去锁则不误解锁后来者。 */
  public void releaseLoad(String logical, String owner) {
    if (owner == null) return;
    try {
      RLock lock = client().getLock(lockKey(logical));
      if (owner.equals(ownerToken()) && lock.isHeldByCurrentThread()) lock.unlock();
    } catch (RuntimeException ex) { failure(ex); }
  }

  public void discard(String logical, String generation) {
    if (!available()) return;
    try { client().getBucket(key(generation, logical), StringCodec.INSTANCE).delete(); }
    catch (RuntimeException ex) { failure(ex); }
  }

  /** 提交后切换代际并广播失效；写后失效不跳过冷却，尽力避免旧展示。 */
  public void invalidate() {
    if (!properties.isEnabled()) return;
    try {
      RedissonSupport.eval(client(), INVALIDATE, RScript.ReturnType.LONG,
          List.of(generationKey()), UUID.randomUUID().toString(), channel());
      retryAt.set(0);
    } catch (RuntimeException ex) { failure(ex); }
  }

  public void failure(RuntimeException ex) {
    long now = System.nanoTime();
    long previous = retryAt.getAndSet(now + properties.getRedisCooldown().toNanos());
    metrics.increment("redis_error");
    if (previous <= now) LoggerFactory.getLogger(getClass()).warn(
        "Redisson 商品缓存不可用，短暂冷却并有界回源：{}", ex.getClass().getSimpleName());
  }

  public Map<String,Object> status() {
    return Map.of("enabled", properties.isEnabled(), "circuitOpen", properties.isEnabled() && !available(),
        "namespace", properties.getNamespace(), "client", "Redisson");
  }

  /** 一次 Redis 查询的结果。 generation 说明查询属于哪一代缓存，value 是缓存内容；后续回填必须继续核对这一代是否仍有效。 */
  public static final class Lookup {
    /** Redis 缓存代际编号，变更后旧代数据不再可达。 */
    private final String generation;

    /** Redis 中的序列化内容，为空表示缓存未命中。 */
    private final String value;

    /** 保存这一次内部计算的数据，创建后通过 Getter 读取。 这个内部对象由 Java 代码直接创建，不需要从 HTTP 请求或缓存 JSON 恢复。 */
    public Lookup(String generation, String value) {
      this.generation = generation;
      this.value = value;
    }

    /** 获取Redis 缓存代际编号，变更后旧代数据不再可达。 */
    public String getGeneration() {
      return generation;
    }

    /** 获取Redis 中的序列化内容，为空表示缓存未命中。 */
    public String getValue() {
      return value;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      Lookup that = (Lookup) other;
      return Objects.equals(generation, that.generation) && Objects.equals(value, that.value);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
      return Objects.hash(generation, value);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
      return "Lookup[" + "generation=" + generation + ", value=" + value + "]";
    }
  }
}
