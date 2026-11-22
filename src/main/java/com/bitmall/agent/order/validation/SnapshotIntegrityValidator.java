package com.bitmall.agent.order.validation;

import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.service.OrderSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;

/** 首先核对后端快照摘要；摘要用于内容校验，不是购买幂等编号。 */
@Component
@Order(10)
@RequiredArgsConstructor
public class SnapshotIntegrityValidator implements OrderValidator {
  /** 读取购买快照并重新计算摘要，确认快照没有被改动。 */
  private final OrderSnapshotService snapshots;

  @Override
  public void validate(OrderValidationContext context) {
    OrderTraceDO trace = context.getTrace();
    if (!snapshots.hash(trace.getRequestSnapshot()).equals(trace.getRequestHash())) {
      throw new OrderRejectedException("SNAPSHOT_INVALID", "下单快照完整性校验失败");
    }
    context.setSnapshot(snapshots.read(trace.getRequestSnapshot()));
  }
}
