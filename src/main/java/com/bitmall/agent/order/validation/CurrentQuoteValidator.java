package com.bitmall.agent.order.validation;

import com.bitmall.agent.order.service.OrderSnapshotService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 从权威数据重算商品、地址和优惠券；条件变化时要求重新确认。 */
@Component
@Order(30)
public class CurrentQuoteValidator implements OrderValidator {
  private final OrderSnapshotService snapshots;

  public CurrentQuoteValidator(OrderSnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @Override
  public void validate(OrderValidationContext context) {
    context.setQuote(snapshots.refresh(context.getUserId(), context.getSnapshot()));
  }
}
