package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.monitor.OrderMetrics;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 异步下单的受理（排队）事务。
 *
 * <p>这里不建单，只是在一个短事务里：核对 Redis 库存预占已完成、把 trace 改为 PROCESSING、写入一条 ORDER_BUILD
 * 建单消息。事务提交后消息才会投递，由消费者调用 {@link OrderCreationService#createQueued} 真正建单。 优惠券在建单事务里才锁定，这里不碰。
 */
@Service
@RequiredArgsConstructor
public class OrderAsyncAdmissionService {
  private static final String STATUS_INIT = "INIT";
  private static final String MODE_ASYNC = "ASYNC";

  /** 锁定读取 trace，并用版本号 CAS 抢占受理权、写入排队处理期限。 */
  private final OrderTraceRepository traces;

  /** 在同一事务里写入 ORDER_BUILD 建单消息（Outbox），提交后再投递。 */
  private final OrderMessageService messages;

  /** 受理前确认异步队列当前可用（已启用且 Broker 健康）。 */
  private final OrderSubmissionPolicy policy;

  /** 读取排队处理期限（秒）：超过这个时间还没建单，任务按超时失败处理。 */
  private final OrderProcessingProperties properties;

  /** 记录重复提交次数和异步受理成功数。 */
  private final OrderMetrics metrics;

  /** 核对本次购买的 Redis 预占仍然有效，并把库存意图标记为已受理。 */
  private final OrderInventoryService inventory;

  /**
   * 受理一笔异步购买。trace 改为 PROCESSING 与建单消息在同一事务提交；Agent 确认执行时会加入外层事务。
   *
   * <p>trace 已不在 INIT 状态说明是重复提交，直接返回。
   */
  @Transactional
  public void admit(long user, String id) {
    OrderTraceDO trace = traces.lock(id);
    if (trace.getUserId() != user) {
      throw new IllegalArgumentException("下单任务不存在或不属于当前用户");
    }
    if (!STATUS_INIT.equals(trace.getStatus())) {
      metrics.event("repeat_submit");
      return;
    }
    if (!MODE_ASYNC.equals(trace.getMode())) {
      throw new IllegalArgumentException("该购买凭证不是排队下单");
    }
    boolean confirmationExpired = !trace.getExpiresAt().isAfter(LocalDateTime.now());
    if (confirmationExpired) {
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    }
    policy.requireQueueAvailable();

    LocalDateTime processingDeadline =
        LocalDateTime.now().plusSeconds(properties.getAsyncTimeoutSeconds());
    if (!traces.claim(trace, processingDeadline)) {
      return;
    }
    // claim 后重新锁定读取（版本号已变），确认预占没有在此期间被恢复任务释放。
    inventory.verifyPrepared(traces.lock(id));
    inventory.admitted(id);
    messages.enqueueBuild(id);
    metrics.committed("accepted", MODE_ASYNC);
  }
}
