package com.bitmall.agent.order.service;

import com.bitmall.agent.common.idempotent.Idempotent;
import com.bitmall.agent.order.exception.OrderRejectedException;
import org.springframework.stereotype.Service;

/** 异步建单的消费边界。临时异常继续重试，只有已确认的业务拒绝在回滚后保存失败。 */
@Service
public class OrderBuildCommandService {
  private final OrderCreationService creation;
  private final OrderTraceFailureService failures;

  public OrderBuildCommandService(
      OrderCreationService creation, OrderTraceFailureService failures) {
    this.creation = creation;
    this.failures = failures;
  }

  /** Redis 快速去重可能失效，真正建单仍由 trace 行锁、终态和订单唯一关联兜底。 */
  @Idempotent(scene = "ORDER_BUILD", key = "#id", mq = true)
  public void consume(String id) {
    try {
      creation.createQueued(id);
    } catch (OrderRejectedException rejected) {
      failures.fail(id, rejected.getCode(), rejected.getMessage());
    }
  }
}
