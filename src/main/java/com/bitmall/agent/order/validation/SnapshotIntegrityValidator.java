package com.bitmall.agent.order.validation;

import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.service.OrderSnapshotService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 首先核对后端快照摘要；摘要用于内容校验，不是购买幂等编号。 */
@Component
@Order(10)
public class SnapshotIntegrityValidator implements OrderValidator {
  private final OrderSnapshotService snapshots;

  public SnapshotIntegrityValidator(OrderSnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @Override
  public void validate(OrderValidationContext context) {
    var trace = context.getTrace();
    if (!snapshots.hash(trace.getRequestSnapshot()).equals(trace.getRequestHash()))
      throw new OrderRejectedException("SNAPSHOT_INVALID", "下单快照完整性校验失败");
    context.setSnapshot(snapshots.read(trace.getRequestSnapshot()));
  }
}
