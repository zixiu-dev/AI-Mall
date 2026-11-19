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

/** Redisson 商品缓存适配。普通字符串、RLock、RTopic 共用缓存连接，脚本保留回填凭据与所有者的原子检查。 */
@Component
public class RedisCatalogStore {
  // 缺失键临时放一个回填凭据。删除键后，即使较早的数据库查询才返回，也不能写回旧值。
  private static final String LOOKUP =
      "local r={} for i,k in ipairs(KEYS) do local v=redis.call('GET',k); if not v then v=ARGV[1];"
          + " redis.call('SET',k,v,'PX',60000) end; r[i]=v end; return r";
  private static final String FILL =
      "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end;"
          + " redis.call('PSETEX',KEYS[1],ARGV[2],ARGV[3]);return 1";
  private static final String OWNED_FILL =
      "if redis.call('GET',KEYS[1])~=ARGV[1] or redis.call('HEXISTS',KEYS[2],ARGV[4])~=1 then"
          + " return 0 end; redis.call('PSETEX',KEYS[1],ARGV[2],ARGV[3]);return 1";
  private final ObjectProvider<RedissonClient> clients;
  private final CatalogCacheProperties properties;
  private final CacheMetrics metrics;
  private final AtomicLong retryAt = new AtomicLong();

  public RedisCatalogStore(
      @Qualifier("catalogRedisson") ObjectProvider<RedissonClient> clients,
      CatalogCacheProperties properties,
      CacheMetrics metrics) {
    this.clients = clients;
    this.properties = properties;
    this.metrics = metrics;
  }

  /** 按需取得 Redisson，冷却期间不因普通读取反复连接故障 Redis。 */
  public RedissonClient client() {
    return clients.getObject();
  }

  public String channel() {
    return properties.getNamespace() + ":display:invalidate";
  }

  public String key(String logical) {
    return properties.getNamespace() + ":{catalog}:display:" + logical;
  }

  /** 新锁使用独立版本键，避免与旧 SET NX 字符串锁产生 Redis 类型冲突。 */
  public String lockKey(String logical) {
    return properties.getNamespace() + ":{catalog}:load-lock:redisson:v2:" + logical;
  }

  public boolean available() {
    return properties.isEnabled() && System.nanoTime() >= retryAt.get();
  }

  /** 同一批 SKU 的价格和库存一次往返；缓存不可用时返回空映射，由调用者有界回源。 */
  public Map<String, Lookup> lookupMany(List<String> names) {
    if (names.isEmpty() || !available()) return Map.of();
    try {
      List<String> raw =
          RedissonSupport.eval(
              client(),
              LOOKUP,
              RScript.ReturnType.LIST,
              names.stream().map(this::key).toList(),
              "~" + UUID.randomUUID());
      var result = new LinkedHashMap<String, Lookup>();
      for (int i = 0; i < names.size(); i++) {
        String value = raw.get(i);
        result.put(
            names.get(i),
            value.startsWith("~") ? new Lookup(value, null) : new Lookup(null, value));
      }
      return result;
    } catch (RuntimeException ex) {
      failure(ex);
      return Map.of();
    }
  }

  /** 批量回填本轮仍有效的缺失键，过期或已被交易删除的凭据一律跳过。 */
  public void fillOffers(
      String lock,
      String owner,
      Map<String, Lookup> reads,
      Map<String, String> writes,
      java.util.function.ToLongFunction<String> ttl) {
    var keys = new ArrayList<String>();
    var args = new ArrayList<Object>();
    writes.forEach(
        (name, value) -> {
          var read = reads.get(name);
          if (read != null && read.getTicket() != null) {
            keys.add(key(name));
            args.add(read.getTicket());
            args.add(String.valueOf(ttl.applyAsLong(name)));
            args.add(value);
          }
        });
    if (keys.isEmpty() || owner == null) return;
    keys.add(lockKey(lock));
    args.add(owner);
    try {
      RedissonSupport.eval(
          client(),
          "if redis.call('HEXISTS',KEYS[#KEYS],ARGV[#ARGV])~=1 then return 0 end; for i=1,#KEYS-1"
              + " do local k=KEYS[i]; local j=i*3-2; if redis.call('GET',k)==ARGV[j] then"
              + " redis.call('PSETEX',k,ARGV[j+1],ARGV[j+2]) end end;return 1",
          RScript.ReturnType.LONG,
          keys,
          args.toArray());
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  public Lookup lookup(String logical) {
    return lookupMany(List.of(logical)).get(logical);
  }

  /** 无业务重建的测试/维护回填入口；生产回源使用 fillOwned。 */
  public boolean fill(String logical, String ticket, String value, Duration ttl) {
    if (!available() || ticket == null) return false;
    try {
      return Long.valueOf(1)
          .equals(
              RedissonSupport.eval(
                  client(),
                  FILL,
                  RScript.ReturnType.LONG,
                  List.of(key(logical)),
                  ticket,
                  String.valueOf(ttl.toMillis()),
                  value));
    } catch (RuntimeException ex) {
      failure(ex);
      return false;
    }
  }

  /** true 已持有跨实例锁；false 别人正在重建；null 基础设施故障，改走受限回源。未指定固定租约，使用看门狗。 */
  public Boolean acquireLoad(String logical) {
    if (!available()) return null;
    try {
      return client().getLock(lockKey(logical)).tryLock();
    } catch (RuntimeException ex) {
      failure(ex);
      return null;
    }
  }

  /** 与 Redisson RLock 一致的所有者名，回填脚本需要在 Redis 内核对它。 */
  public String ownerToken() {
    return client().getId() + ":" + Thread.currentThread().getId();
  }

  /** 回填凭据与 RLock 所有权同时成立才写缓存，暂停后失去锁的旧线程不能污染新结果。 */
  public boolean fillOwned(
      String logical, String ticket, String value, Duration ttl, String owner) {
    if (!available() || ticket == null || owner == null) return false;
    try {
      return Long.valueOf(1)
          .equals(
              RedissonSupport.eval(
                  client(),
                  OWNED_FILL,
                  RScript.ReturnType.LONG,
                  List.of(key(logical), lockKey(logical)),
                  ticket,
                  String.valueOf(ttl.toMillis()),
                  value,
                  owner));
    } catch (RuntimeException ex) {
      failure(ex);
      return false;
    }
  }

  /** 仅解开当前线程仍持有的锁；正常完成停止看门狗，异常失去锁则不误解锁后来者。 */
  public void releaseLoad(String logical, String owner) {
    if (owner == null) return;
    try {
      RLock lock = client().getLock(lockKey(logical));
      if (owner.equals(ownerToken()) && lock.isHeldByCurrentThread()) lock.unlock();
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /** 发现损坏的值时只移除该值，不能误删并发产生的新结果。 */
  public void discard(String logical, String badValue) {
    if (!available()) return;
    try {
      client().getBucket(key(logical), StringCodec.INSTANCE).compareAndSet(badValue, null);
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /** 库存不进本地缓存；商品资料单键失效时额外通知其他实例清对应 L1。 */
  public void invalidate(List<String> names, boolean broadcast) {
    if (!properties.isEnabled() || names.isEmpty()) return;
    try {
      RedissonSupport.eval(
          client(),
          "for i,k in ipairs(KEYS) do redis.call('DEL',k); if ARGV[1]=='1' then"
              + " redis.call('PUBLISH',ARGV[2],ARGV[i+2]) end end; return 1",
          RScript.ReturnType.LONG,
          names.stream().map(this::key).toList(),
          java.util.stream.Stream.concat(
                  java.util.stream.Stream.of(broadcast ? "1" : "0", channel()), names.stream())
              .toArray());
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  public void failure(RuntimeException ex) {
    long now = System.nanoTime();
    long previous = retryAt.getAndSet(now + properties.getRedisCooldown().toNanos());
    metrics.increment("redis_error");
    if (previous <= now)
      LoggerFactory.getLogger(getClass())
          .warn("Redisson 商品缓存不可用，短暂冷却并有界回源：{}", ex.getClass().getSimpleName());
  }

  public Map<String, Object> status() {
    return Map.of(
        "enabled",
        properties.isEnabled(),
        "circuitOpen",
        properties.isEnabled() && !available(),
        "namespace",
        properties.getNamespace(),
        "client",
        "Redisson");
  }

  /** 一次 Redis 查询的结果。 命中时仅返回 value，未命中时仅返回短期 ticket，删除 key 即作废 ticket。 */
  public static final class Lookup {
    /** 该键本轮缺失的回填凭据。 */
    private final String ticket;

    /** Redis 中的序列化内容，为空表示缓存未命中。 */
    private final String value;

    /** 保存这一次内部计算的数据，创建后通过 Getter 读取。 这个内部对象由 Java 代码直接创建，不需要从 HTTP 请求或缓存 JSON 恢复。 */
    public Lookup(String ticket, String value) {
      this.ticket = ticket;
      this.value = value;
    }

    /** 获取该键本轮缺失的回填凭据。 */
    public String getTicket() {
      return ticket;
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
      return Objects.equals(ticket, that.ticket) && Objects.equals(value, that.value);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
      return Objects.hash(ticket, value);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
      return "Lookup[" + "ticket=" + ticket + ", value=" + value + "]";
    }
  }
}
