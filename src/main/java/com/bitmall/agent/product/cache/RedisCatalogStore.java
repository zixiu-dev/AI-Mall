package com.bitmall.agent.product.cache;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 商品展示缓存的 Redis 读写（基于 Redisson）。普通字符串值、回源锁（RLock）和失效广播（RTopic）共用同一个缓存连接。
 *
 * <p><b>回填凭据（ticket）机制</b>：读缓存时，如果 key 不存在，脚本会顺手写入一个以 {@code ~} 开头的随机凭据（60 秒过期）。 之后回源数据库拿到结果，只有 key
 * 上还是这个凭据时才允许写回。这样如果在查询数据库期间商品被修改、key 被删除， 凭据也随之作废，较早发起的数据库查询结果就不会把旧值写回缓存。
 *
 * <p><b>熔断</b>：任何 Redis 操作失败都会调用 {@link #failure}，在冷却期内 {@link #available()} 返回 false，
 * 调用方改走有界的数据库回源，避免反复连接故障的 Redis。
 */
@Component
@RequiredArgsConstructor
public class RedisCatalogStore {
  private static final Logger log = LoggerFactory.getLogger(RedisCatalogStore.class);

  /** 回填凭据前缀；缓存值本身不会以它开头，所以可以据此区分"命中的值"和"未命中的凭据"。 */
  private static final String TICKET_PREFIX = "~";

  /** 批量读取；缺失的 key 写入凭据（ARGV[1]）并返回凭据，存在的 key 返回原值。 */
  private static final String LOOKUP =
      "local r={} for i,k in ipairs(KEYS) do local v=redis.call('GET',k); if not v then v=ARGV[1];"
          + " redis.call('SET',k,v,'PX',60000) end; r[i]=v end; return r";

  /** key 上仍是本次凭据时写入值。 */
  private static final String FILL =
      "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end;"
          + " redis.call('PSETEX',KEYS[1],ARGV[2],ARGV[3]);return 1";

  /** 在 FILL 的基础上，还要求当前线程仍持有回源锁（KEYS[2] 是 RLock 的 hash，ARGV[4] 是持有者）。 */
  private static final String OWNED_FILL =
      "if redis.call('GET',KEYS[1])~=ARGV[1] or redis.call('HEXISTS',KEYS[2],ARGV[4])~=1 then"
          + " return 0 end; redis.call('PSETEX',KEYS[1],ARGV[2],ARGV[3]);return 1";

  /** 批量版 OWNED_FILL。KEYS 最后一个是锁 key，ARGV 最后一个是持有者；其余每个 key 对应 ARGV 里连续三项：凭据、TTL 毫秒、值。 */
  private static final String OWNED_FILL_MANY =
      "if redis.call('HEXISTS',KEYS[#KEYS],ARGV[#ARGV])~=1 then return 0 end; for i=1,#KEYS-1"
          + " do local k=KEYS[i]; local j=i*3-2; if redis.call('GET',k)==ARGV[j] then"
          + " redis.call('PSETEX',k,ARGV[j+1],ARGV[j+2]) end end;return 1";

  /** 删除一批 key；ARGV[1]=='1' 时为每个 key 向 ARGV[2] 频道广播一条失效消息（消息内容为逻辑名）。 */
  private static final String INVALIDATE =
      "for i,k in ipairs(KEYS) do redis.call('DEL',k); if ARGV[1]=='1' then"
          + " redis.call('PUBLISH',ARGV[2],ARGV[i+2]) end end; return 1";

  private static final Long SCRIPT_SUCCESS = 1L;

  /** 商品缓存专用 Redisson 客户端（catalogRedisson），首次使用时才建立连接。 */
  @Qualifier("catalogRedisson")
  private final ObjectProvider<RedissonClient> clients;

  /** 读取缓存开关、key 命名空间和 Redis 故障后的熔断冷却时长。 */
  private final CatalogCacheProperties properties;

  /** 记录 Redis 故障次数。 */
  private final CacheMetrics metrics;

  /** 熔断结束的时间点（System.nanoTime）；在此之前视为 Redis 不可用。 */
  private final AtomicLong retryAt = new AtomicLong();

  /** 首次调用时才建立 Redisson 连接。 */
  public RedissonClient client() {
    return clients.getObject();
  }

  /** 失效广播频道名。 */
  public String channel() {
    return properties.getNamespace() + ":display:invalidate";
  }

  /** 逻辑名 → Redis key。所有 key 用 {catalog} 作 hash tag，保证批量脚本里的 key 在同一个 Cluster 槽。 */
  public String key(String logical) {
    return properties.getNamespace() + ":{catalog}:display:" + logical;
  }

  /** 回源锁的 key。带独立的 "redisson:v2" 版本段，避免与旧版 SET NX 字符串锁同名而产生 Redis 类型冲突。 */
  public String lockKey(String logical) {
    return properties.getNamespace() + ":{catalog}:load-lock:redisson:v2:" + logical;
  }

  /** 缓存已启用且不在熔断冷却期内。 */
  public boolean available() {
    return properties.isEnabled() && System.nanoTime() >= retryAt.get();
  }

  /**
   * 一次往返批量读取多个 key（例如同一商品所有 SKU 的价格和库存）。
   *
   * @return 逻辑名 → 读取结果，保持入参顺序；缓存不可用或出错时返回空映射，由调用方有界回源
   */
  public Map<String, Lookup> lookupMany(List<String> names) {
    if (names.isEmpty() || !available()) {
      return Map.of();
    }
    try {
      String ticket = TICKET_PREFIX + UUID.randomUUID();
      List<String> rawValues =
          RedissonSupport.eval(
              client(),
              LOOKUP,
              RScript.ReturnType.LIST,
              names.stream().map(this::key).toList(),
              ticket);
      Map<String, Lookup> result = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        String rawValue = rawValues.get(i);
        Lookup lookup =
            rawValue.startsWith(TICKET_PREFIX)
                ? new Lookup(rawValue, null)
                : new Lookup(null, rawValue);
        result.put(names.get(i), lookup);
      }
      return result;
    } catch (RuntimeException ex) {
      failure(ex);
      return Map.of();
    }
  }

  public Lookup lookup(String logical) {
    return lookupMany(List.of(logical)).get(logical);
  }

  /**
   * 批量回填 SKU 报价。只回填本轮读取时缺失（拿到了凭据）的 key，且要求当前线程仍持有回源锁； 凭据已过期或 key 已被交易删除的，在脚本里逐个跳过。
   *
   * @param lock 回源锁的逻辑名
   * @param owner 锁持有者，为 null（没拿到跨实例锁）时不回填
   * @param reads 本轮 {@link #lookupMany} 的结果，提供每个 key 的凭据
   * @param writes 要回填的逻辑名 → 值
   * @param ttl 按逻辑名给出 TTL 毫秒
   */
  public void fillOffers(
      String lock,
      String owner,
      Map<String, Lookup> reads,
      Map<String, String> writes,
      ToLongFunction<String> ttl) {
    List<String> keys = new ArrayList<>();
    List<Object> args = new ArrayList<>();
    writes.forEach(
        (name, value) -> {
          Lookup read = reads.get(name);
          boolean hasTicket = read != null && read.getTicket() != null;
          if (hasTicket) {
            keys.add(key(name));
            args.add(read.getTicket());
            args.add(String.valueOf(ttl.applyAsLong(name)));
            args.add(value);
          }
        });
    if (keys.isEmpty() || owner == null) {
      return;
    }
    keys.add(lockKey(lock));
    args.add(owner);
    try {
      RedissonSupport.eval(
          client(), OWNED_FILL_MANY, RScript.ReturnType.LONG, keys, args.toArray());
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /** 不检查锁所有权的回填，只给测试和维护场景使用；生产回源走 {@link #fillOwned}。 */
  public boolean fill(String logical, String ticket, String value, Duration ttl) {
    if (!available() || ticket == null) {
      return false;
    }
    try {
      Object result =
          RedissonSupport.eval(
              client(),
              FILL,
              RScript.ReturnType.LONG,
              List.of(key(logical)),
              ticket,
              String.valueOf(ttl.toMillis()),
              value);
      return SCRIPT_SUCCESS.equals(result);
    } catch (RuntimeException ex) {
      failure(ex);
      return false;
    }
  }

  /**
   * 尝试获取跨实例回源锁，不等待。未指定租约时间，由 Redisson 看门狗自动续期。
   *
   * @return true 拿到锁；false 别的实例正在重建；null Redis 不可用，调用方改走本机限流的回源
   */
  public Boolean acquireLoad(String logical) {
    if (!available()) {
      return null;
    }
    try {
      return client().getLock(lockKey(logical)).tryLock();
    } catch (RuntimeException ex) {
      failure(ex);
      return null;
    }
  }

  /** 当前线程在 Redisson RLock 里的持有者名（格式与 Redisson 内部一致），回填脚本用它核对锁所有权。 */
  public String ownerToken() {
    return client().getId() + ":" + Thread.currentThread().getId();
  }

  /**
   * 单 key 回填：凭据仍有效且当前线程仍持有回源锁时才写入。
   *
   * <p>要同时检查锁，是因为线程可能在查询数据库时长时间卡住、锁已过期被别人拿走，这时它的旧结果不能覆盖新结果。
   */
  public boolean fillOwned(
      String logical, String ticket, String value, Duration ttl, String owner) {
    if (!available() || ticket == null || owner == null) {
      return false;
    }
    try {
      Object result =
          RedissonSupport.eval(
              client(),
              OWNED_FILL,
              RScript.ReturnType.LONG,
              List.of(key(logical), lockKey(logical)),
              ticket,
              String.valueOf(ttl.toMillis()),
              value,
              owner);
      return SCRIPT_SUCCESS.equals(result);
    } catch (RuntimeException ex) {
      failure(ex);
      return false;
    }
  }

  /** 释放回源锁，同时停止看门狗续期。只释放当前线程仍持有的锁：如果锁已经过期并被别人拿走，不能把别人的锁解掉。 */
  public void releaseLoad(String logical, String owner) {
    if (owner == null) {
      return;
    }
    try {
      RLock lock = client().getLock(lockKey(logical));
      if (owner.equals(ownerToken()) && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /** 删除一个格式损坏的缓存值。用 CAS 只删这个坏值，如果并发请求已经写入了新值，不会被误删。 */
  public void discard(String logical, String badValue) {
    if (!available()) {
      return;
    }
    try {
      client().getBucket(key(logical), StringCodec.INSTANCE).compareAndSet(badValue, null);
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /**
   * 删除一批缓存 key。
   *
   * @param broadcast 为 true 时同时通知其他实例清掉各自的本地（L1）缓存；库存不进本地缓存，删除库存 key 时传 false
   */
  public void invalidate(List<String> names, boolean broadcast) {
    if (!properties.isEnabled() || names.isEmpty()) {
      return;
    }
    try {
      List<String> keys = names.stream().map(this::key).toList();
      Object[] args =
          Stream.concat(Stream.of(broadcast ? "1" : "0", channel()), names.stream()).toArray();
      RedissonSupport.eval(client(), INVALIDATE, RScript.ReturnType.LONG, keys, args);
    } catch (RuntimeException ex) {
      failure(ex);
    }
  }

  /** 记录一次 Redis 故障并开启（或延长）熔断冷却；同一冷却期内只打一次警告日志。 */
  public void failure(RuntimeException ex) {
    long now = System.nanoTime();
    long previousRetryAt = retryAt.getAndSet(now + properties.getRedisCooldown().toNanos());
    metrics.increment("redis_error");
    boolean wasAvailable = previousRetryAt <= now;
    if (wasAvailable) {
      log.warn("Redisson 商品缓存不可用，短暂冷却并有界回源：{}", ex.getClass().getSimpleName());
    }
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

  /** 一次缓存读取的结果：命中时只有 value；未命中时只有 ticket（回填凭据），删除 key 即作废 ticket。 */
  @Getter
  @EqualsAndHashCode
  @ToString
  public static final class Lookup {
    private final String ticket;

    /** 缓存里的序列化内容，为 null 表示未命中。 */
    private final String value;

    public Lookup(String ticket, String value) {
      this.ticket = ticket;
      this.value = value;
    }
  }
}
