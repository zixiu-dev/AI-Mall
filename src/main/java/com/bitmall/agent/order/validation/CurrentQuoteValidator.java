package com.bitmall.agent.order.validation;

import com.bitmall.agent.order.service.OrderSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 从权威数据重算商品、地址和优惠券；条件变化时要求重新确认。 */
@Component
@Order(30)
@RequiredArgsConstructor
public class CurrentQuoteValidator implements OrderValidator {
  /** 按当前价格、库存和优惠重新试算，与购买快照比较。 */
  private final OrderSnapshotService snapshots;

  @Override
  public void validate(OrderValidationContext context) {
    context.setQuote(snapshots.refresh(context.getUserId(), context.getSnapshot()));
  }
}
