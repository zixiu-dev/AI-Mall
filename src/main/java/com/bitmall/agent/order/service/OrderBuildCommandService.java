package com.bitmall.agent.order.service;

import com.bitmall.agent.common.idempotent.Idempotent;
import com.bitmall.agent.order.exception.OrderRejectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 异步建单的消费边界。临时异常继续重试，只有已确认的业务拒绝在回滚后保存失败。 */
@Service
@RequiredArgsConstructor
public class OrderBuildCommandService {
  /** 在同一事务内扣库存、锁定券、创建订单并写入 trace 成功状态。 */
  private final OrderCreationService creation;

  /** 建单被业务规则拒绝时，把购买记为失败。 */
  private final OrderTraceFailureService failures;

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
