package com.bitmall.agent.order.validation;

/** 下单责任链的一个检查步骤。只校验或补齐上下文，库存和券占用由建单事务统一执行。 */
public interface OrderValidator {
  void validate(OrderValidationContext context);
}
