package com.bitmall.agent.common.idempotent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
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

/** 注解入口：接口获取分布式锁，MQ 获取带所有者的消费标记。订单状态、CAS 与成功事务仍由业务服务处理。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyAspect {
  /** 统计切面入口和被锁挡住的重复请求，使用有限场景标签。 */
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  /** 与业务无关的锁和消费标记后端。 */
  private final IdempotencyBackend backend;

  /** 空 SpEL 时序列化方法参数生成防并发摘要，不作为永久订单编号。 */
  private final ObjectMapper json;

  /** 注入后端与 JSON 工具。 */
  public IdempotencyAspect(
      IdempotencyBackend backend,
      ObjectMapper json,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.backend = backend;
    this.json = json;
    this.metrics = metrics;
  }

  /** 包住外部代理调用；事务已经存在时，将解锁与消费成功标记推迟到最外层事务完成。 */
  @Around("@annotation(rule)")
  public Object around(ProceedingJoinPoint point, Idempotent rule) throws Throwable {
    metrics.counter("mall.idempotent.requests", "scene", rule.scene()).increment();
    var method = ((MethodSignature) point.getSignature()).getMethod();
    String businessKey;
    if (rule.key().isBlank()) {
      businessKey =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          json.writeValueAsString(point.getArgs())
                              .getBytes(StandardCharsets.UTF_8)));
    } else {
      var context =
          new MethodBasedEvaluationContext(
              point.getTarget(), method, point.getArgs(), new DefaultParameterNameDiscoverer());
      Object parsed = new SpelExpressionParser().parseExpression(rule.key()).getValue(context);
      if (parsed == null || parsed.toString().isBlank())
        throw new IllegalArgumentException("缺少幂等业务编号");
      businessKey = parsed.toString();
    }
    String key = rule.scene() + ":" + businessKey;
    if (rule.mq()) return consume(point, key, rule.scene());
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

  /** MQ 已处理时直接确认；消费中抛出重试，业务异常不能被误标为成功。 */
  private Object consume(ProceedingJoinPoint point, String key, String scene) throws Throwable {
    String token = backend.begin(key);
    if ("DONE".equals(token)) {
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

  /** 有外层事务时等提交或回滚后再执行；否则目标方法自己的事务已在返回前结束。 */
  private void afterTransaction(Runnable committed, Runnable rolledBack) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            /** 事务完成后才释放句柄或确认消费标记。 */
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_COMMITTED) committed.run();
              else rolledBack.run();
            }
          });
    } else committed.run();
  }

  /** 解锁异常不能把已成功提交的业务伪装为失败。 */
  private void close(AutoCloseable lease) {
    try {
      lease.close();
    } catch (Exception ignored) {
      /* 锁有租约，后端负责记录异常。 */
    }
  }
}
