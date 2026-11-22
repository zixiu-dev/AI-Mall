package com.bitmall.agent.order.idempotent;

import com.bitmall.agent.common.idempotent.IdempotencyBackend;
import com.bitmall.agent.common.idempotent.IdempotentBusyException;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 通用幂等接口的 Redis 实现，提供两种能力：
 *
 * <ul>
 *   <li>{@link #lock}：下单请求的分布式锁，减少同一购买请求的并发处理；
 *   <li>{@link #begin}/{@link #complete}/{@link #failed}：MQ 消费去重标记。
 * </ul>
 *
 * <p>Redis 在这里只是第一道防线，数据库的状态 CAS 才是最终裁决：锁可能因 Redis 故障或租约过期丢失， 所以业务代码拿到锁后仍必须走数据库 CAS
 * 检查。消费标记的值里带随机令牌，只有令牌匹配才能修改或删除， 防止一个卡顿很久的旧消费者删掉新消费者的标记。
 */
@Component
@RequiredArgsConstructor
public class OrderIdempotencyBackend implements IdempotencyBackend {
  private static final Logger log = LoggerFactory.getLogger(OrderIdempotencyBackend.class);

  /** 锁功能关闭或 Redis 降级时返回的令牌，表示"没有 Redis 标记，完全交给数据库判断"。 */
  private static final String TOKEN_DISABLED = "DISABLED";

  /** 消费已完成的标记值。 */
  private static final String MARK_DONE = "DONE";

  private static final String PROCESSING_TOKEN_PREFIX = "PROCESSING:";

  private static final AutoCloseable NO_LOCK = () -> {};

  /** 原子地创建"消费中"标记（10 分钟过期）。 已有标记时不覆盖，返回已有的值；没有标记时写入令牌并返回 nil。 */
  private static final DefaultRedisScript<String> BEGIN =
      new DefaultRedisScript<>(
          "local old=redis.call('get',KEYS[1]);if old then return old"
              + " end;redis.call('set',KEYS[1],ARGV[1],'EX',600);return nil",
          String.class);

  /** 令牌匹配时把标记改为 DONE（保留 2 小时），用于识别之后的重复消息。 */
  private static final DefaultRedisScript<Long> COMPLETE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then"
              + " redis.call('set',KEYS[1],'DONE','EX',7200);return 1 end;return 0",
          Long.class);

  /** 令牌匹配时删除标记，允许消息重试；不会删掉别人的标记。 */
  private static final DefaultRedisScript<Long> DELETE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1])"
              + " end;return 0",
          Long.class);

  /** 交易专用的 Redisson 客户端，首次使用时才建立连接。 */
  @Qualifier("orderRedisson")
  private final ObjectProvider<RedissonClient> redisson;

  /** MQ 消费去重使用的交易 Redis 连接。 */
  private final OrderRedisClient redis;

  /** 读取锁开关、Redis 故障时是否允许数据库兜底，以及 Redis key 命名空间。 */
  private final OrderProcessingProperties properties;

  /**
   * 尝试获取分布式锁（Redisson 看门狗自动续期），不等待。
   *
   * <p>两种失败要区分对待：
   *
   * <ul>
   *   <li>锁被别人持有：抛 {@link IdempotentBusyException}，这是正常的并发冲突，不降级；
   *   <li>Redis 本身故障：配置允许数据库兜底时返回一个空锁继续执行，由数据库 CAS 保证正确性；否则抛出原异常。
   * </ul>
   */
  @Override
  public AutoCloseable lock(String key) {
    if (!properties.isLocksEnabled()) {
      return NO_LOCK;
    }
    RLock lock;
    try {
      lock = redisson.getObject().getLock(prefix(key));
      if (!lock.tryLock()) {
        throw new IdempotentBusyException();
      }
    } catch (IdempotentBusyException busy) {
      throw busy;
    } catch (RuntimeException redisFailure) {
      if (!properties.isDatabaseFallback()) {
        throw redisFailure;
      }
      log.warn("分布式锁暂不可用，使用数据库 CAS 兜底");
      return NO_LOCK;
    }
    return () -> unlockQuietly(lock);
  }

  /** 释放失败不影响业务结果，锁会在租约到期后自动回收。 */
  private static void unlockQuietly(RLock lock) {
    try {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    } catch (RuntimeException ex) {
      log.warn("分布式锁释放失败，将由租约回收");
    }
  }

  /**
   * 开始消费一条 MQ 消息，返回本次消费的令牌。
   *
   * @return 新令牌（首次消费）；"DONE"（已经消费成功过）；"DISABLED"（不使用 Redis 去重）
   * @throws IdempotentBusyException 另一个消费者正在处理同一条消息
   */
  @Override
  public String begin(String key) {
    if (!properties.isLocksEnabled()) {
      return TOKEN_DISABLED;
    }
    String existingMark;
    String token = PROCESSING_TOKEN_PREFIX + UUID.randomUUID();
    try {
      existingMark = redis.template().execute(BEGIN, List.of(prefix(key)), token);
    } catch (RuntimeException redisFailure) {
      if (!properties.isDatabaseFallback()) {
        throw redisFailure;
      }
      log.warn("MQ Redis 去重暂不可用，使用业务数据库兜底");
      return TOKEN_DISABLED;
    }
    if (existingMark == null) {
      return token;
    }
    if (MARK_DONE.equals(existingMark)) {
      return MARK_DONE;
    }
    // 已有别人的"消费中"标记。
    throw new IdempotentBusyException();
  }

  /** 业务事务提交后标记消费完成。标记失败不影响已提交的订单，下次重复消息仍会被数据库识别。 */
  @Override
  public void complete(String key, String token) {
    updateMark(key, token, COMPLETE);
  }

  /** 业务失败后删除自己的"消费中"标记，让消息可以重试。 */
  @Override
  public void failed(String key, String token) {
    updateMark(key, token, DELETE);
  }

  /** 更新标记失败只记日志：数据库里的订单终态才是权威结果。 */
  private void updateMark(String key, String token, DefaultRedisScript<Long> script) {
    if (TOKEN_DISABLED.equals(token)) {
      return;
    }
    try {
      redis.template().execute(script, List.of(prefix(key)), token);
    } catch (RuntimeException ex) {
      log.warn("MQ 消费幂等标记更新失败，后续由数据库识别重复消费");
    }
  }

  /** key 只包含命名空间和业务键，不含时间戳等每次重试都会变的内容，保证重试命中同一个标记。 */
  private String prefix(String key) {
    return properties.getNamespace() + "idempotent:" + key;
  }
}
