package com.bitmall.agent.common.idempotent;

import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link Idempotent} 注解的切面，有两种用法：
 *
 * <ul>
 *   <li>接口方法（mq = false）：执行前获取分布式锁，同一业务 key 的并发请求只放行一个；
 *   <li>MQ 消费方法（mq = true）：执行前写入带令牌的"消费中"标记，已经消费成功的消息直接跳过。
 * </ul>
 *
 * <p>这里只是减少重复执行的第一道防线。订单状态、CAS 检查和事务提交仍由业务服务自己负责。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class IdempotencyAspect {
  /** 消费标记的特殊值：这条消息已经消费成功过。 */
  private static final String TOKEN_DONE = "DONE";

  /** 分布式锁和消费标记的实际实现（交易 Redis）。 */
  private final IdempotencyBackend backend;

  /** 注解没写 key 时，把方法参数序列化后取 SHA-256 作为业务 key。 */
  private final ObjectMapper json;

  /** 按场景统计幂等请求数，以及因并发冲突被拒绝的次数。 */
  private final MeterRegistry metrics;

  /**
   * 包住被注解的方法调用。
   *
   * <p>如果调用时已经处在一个外层事务里，解锁和"消费成功"标记都推迟到最外层事务结束后再做， 否则锁会在事务提交前就释放，另一个请求可能读到尚未提交的状态。
   */
  @Around("@annotation(rule)")
  public Object around(ProceedingJoinPoint point, Idempotent rule) throws Throwable {
    metrics.counter("mall.idempotent.requests", "scene", rule.scene()).increment();
    String key = rule.scene() + ":" + businessKey(point, rule);
    if (rule.mq()) {
      return consume(point, key, rule.scene());
    }

    AutoCloseable lease;
    try {
      lease = backend.lock(key);
    } catch (IdempotentBusyException busy) {
      metrics.counter("mall.idempotent.rejected", "scene", rule.scene()).increment();
      throw busy;
    }
    try {
      return point.proceed();
    } finally {
      afterTransaction(() -> close(lease), () -> close(lease));
    }
  }

  /**
   * 计算业务 key：注解写了 SpEL 表达式（如 {@code "#user + ':' + #id"}）就按表达式取值；没写就对全部参数的 JSON 取 SHA-256。
   *
   * @throws IllegalArgumentException 表达式算出来是 null 或空白
   */
  private String businessKey(ProceedingJoinPoint point, Idempotent rule) throws Exception {
    Method method = ((MethodSignature) point.getSignature()).getMethod();
    if (rule.key().isBlank()) {
      byte[] argumentsJson =
          json.writeValueAsString(point.getArgs()).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(argumentsJson));
    }
    MethodBasedEvaluationContext context =
        new MethodBasedEvaluationContext(
            point.getTarget(), method, point.getArgs(), new DefaultParameterNameDiscoverer());
    Object value = new SpelExpressionParser().parseExpression(rule.key()).getValue(context);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException("缺少幂等业务编号");
    }
    return value.toString();
  }

  /**
   * MQ 消费：已经消费成功过就直接返回（消息会被确认，不再重投）；另一个消费者正在处理时，backend 抛出异常让消息稍后重试。
   *
   * <p>业务抛异常时删除自己的"消费中"标记，绝不能标记为成功。
   */
  private Object consume(ProceedingJoinPoint point, String key, String scene) throws Throwable {
    String token = backend.begin(key);
    if (TOKEN_DONE.equals(token)) {
      metrics.counter("mall.idempotent.duplicates", "scene", scene).increment();
      return null;
    }
    try {
      Object result = point.proceed();
      afterTransaction(() -> backend.complete(key, token), () -> backend.failed(key, token));
      return result;
    } catch (Throwable ex) {
      backend.failed(key, token);
      throw ex;
    }
  }

  /** 当前线程有活动事务时，等它提交/回滚后再执行对应动作；没有事务时（目标方法自己的事务已在返回前结束）立即按"已提交"处理。 */
  private void afterTransaction(Runnable committed, Runnable rolledBack) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      committed.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) {
              committed.run();
            } else {
              rolledBack.run();
            }
          }
        });
  }

  /** 释放锁失败不影响已经成功的业务：锁有租约，到期会自动释放，后端会记录这次异常。 */
  private void close(AutoCloseable lease) {
    try {
      lease.close();
    } catch (Exception ignored) {
      // 见方法说明。
    }
  }
}
