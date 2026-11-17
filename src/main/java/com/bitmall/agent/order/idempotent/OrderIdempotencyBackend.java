package com.bitmall.agent.order.idempotent;

import com.bitmall.agent.common.idempotent.*;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import java.util.List;
import java.util.UUID;
import org.redisson.api.RedissonClient;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 通用幂等接口的 Redis 实现。锁丢失时业务必须继续接受数据库 CAS 检查，消费标记用令牌防止旧持有者误删新标记。 */
@Component
public class OrderIdempotencyBackend implements IdempotencyBackend {
  /** 记录基础设施降级，不输出订单敏感快照。 */
  private static final Logger log = LoggerFactory.getLogger(OrderIdempotencyBackend.class);

  /** 延迟创建 Redisson，关闭锁的 demo 不连接 Redis。 */
  private final ObjectProvider<RedissonClient> redisson;

  /** 独立业务 Redis 模板。 */
  private final OrderRedisClient redis;

  /** 锁开关和环境前缀。 */
  private final OrderProcessingProperties properties;

  /** 令牌匹配时才能变更标记，防止长时间暂停的旧消费者覆盖新消费者。 */
  private static final DefaultRedisScript<Long> COMPLETE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then"
              + " redis.call('set',KEYS[1],'DONE','EX',7200);return 1 end;return 0",
          Long.class);

  /** 异常重试时只能释放自己的令牌。 */
  private static final DefaultRedisScript<Long> DELETE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1])"
              + " end;return 0",
          Long.class);

  /** 注入业务 Redis 和延迟分布式锁客户端。 */
  public OrderIdempotencyBackend(
      @org.springframework.beans.factory.annotation.Qualifier("orderRedisson") ObjectProvider<RedissonClient> redisson,
      OrderRedisClient redis,
      OrderProcessingProperties properties) {
    this.redisson = redisson;
    this.redis = redis;
    this.properties = properties;
  }

  /** 使用看门狗锁；锁服务故障时可按配置交给数据库兜底，抢锁冲突本身不降级。 */
  @Override
  public AutoCloseable lock(String key) {
    if (!properties.isLocksEnabled()) return () -> {};
    org.redisson.api.RLock lock;
    try {
      lock = redisson.getObject().getLock(prefix(key));
      if (!lock.tryLock()) throw new IdempotentBusyException();
    } catch (IdempotentBusyException busy) {
      throw busy;
    } catch (RuntimeException ex) {
      if (!properties.isDatabaseFallback()) throw ex;
      log.warn("分布式锁暂不可用，使用数据库 CAS 兜底");
      return () -> {};
    }
    return () -> {
      try {
        if (lock.isHeldByCurrentThread()) lock.unlock();
      } catch (RuntimeException ex) {
        log.warn("分布式锁释放失败，将由租约回收");
      }
    };
  }

  /** 原子创建消费中标记；Redis 故障时只在允许数据库兜底的场景继续。 */
  @Override
  public String begin(String key) {
    if (!properties.isLocksEnabled()) return "DISABLED";
    try {
      String token = "PROCESSING:" + UUID.randomUUID();
      var script =
          new DefaultRedisScript<String>(
              "local old=redis.call('get',KEYS[1]);if old then return old"
                  + " end;redis.call('set',KEYS[1],ARGV[1],'EX',600);return nil",
              String.class);
      String old = redis.template().execute(script, List.of(prefix(key)), token);
      if (old == null) return token;
      if ("DONE".equals(old)) return "DONE";
    } catch (RuntimeException ex) {
      if (!properties.isDatabaseFallback()) throw ex;
      log.warn("MQ Redis 去重暂不可用，使用业务数据库兜底");
      return "DISABLED";
    }
    throw new IdempotentBusyException();
  }

  /** 业务提交后按所有者令牌标记完成；失败不改变已经提交的订单，下一次仍由数据库识别。 */
  @Override
  public void complete(String key, String token) {
    mark(key, token, true);
  }

  /** 业务失败后移除自己的消费中标记，允许后续重试。 */
  @Override
  public void failed(String key, String token) {
    mark(key, token, false);
  }

  /** 标记更新失败只记录基础设施问题，数据库终态仍是权威结果。 */
  private void mark(String key, String token, boolean success) {
    if ("DISABLED".equals(token)) return;
    try {
      redis.template().execute(success ? COMPLETE : DELETE, List.of(prefix(key)), token);
    } catch (RuntimeException ex) {
      log.warn("MQ 消费幂等标记更新失败，后续由数据库识别重复消费");
    }
  }

  /** 隔离环境和业务场景，不包含每次重试会变化的时间戳。 */
  private String prefix(String key) {
    return properties.getNamespace() + "idempotent:" + key;
  }
}
