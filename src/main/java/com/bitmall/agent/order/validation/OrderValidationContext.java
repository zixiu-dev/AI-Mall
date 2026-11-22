package com.bitmall.agent.order.validation;

import lombok.Getter;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;

/** 同一次责任链共享用户、后端快照和重算报价，避免各步骤重复读取。 */
@Getter
public final class OrderValidationContext {
  /** 当前购买人及已经锁定的购买记录。 */
  private final long userId;

  private final OrderTraceDO trace;

  /** 完整性检查后解析出的确认快照。 */
  private CheckoutQuoteVO snapshot;

  /** 当前条件仍匹配时生成的权威报价。 */
  private CheckoutQuoteVO quote;

  public OrderValidationContext(long userId, OrderTraceDO trace) {
    this.userId = userId;
    this.trace = trace;
  }

  public void setSnapshot(CheckoutQuoteVO snapshot) {
    this.snapshot = snapshot;
  }

  public void setQuote(CheckoutQuoteVO quote) {
    this.quote = quote;
  }
}
