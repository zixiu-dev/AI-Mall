package com.bitmall.agent.order.validation;

import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 按 @Order 顺序执行下单规则，任一步拒绝都会使外层建单事务回滚。 */
@Service
public class OrderValidationChain {
  /** 按 Spring 排序的下单校验器，依次检查购买参数、快照与实时试算。 */
  private final List<OrderValidator> validators;

  public OrderValidationChain(List<OrderValidator> validators) {
    this.validators = List.copyOf(validators);
  }

  public CheckoutQuoteVO validate(long user, OrderTraceDO trace) {
    var context = new OrderValidationContext(user, trace);
    for (OrderValidator validator : validators) {
      validator.validate(context);
    }
    return context.getQuote();
  }
}
