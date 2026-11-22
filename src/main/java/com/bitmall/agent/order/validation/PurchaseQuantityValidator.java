package com.bitmall.agent.order.validation;

import com.bitmall.agent.order.exception.OrderRejectedException;
import java.util.HashSet;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import com.bitmall.agent.cart.vo.CartItemVO;

/** 检查购买项数量和 SKU 唯一性，防止损坏快照或重复规格造成重复占用。 */
@Component
@Order(20)
public class PurchaseQuantityValidator implements OrderValidator {
  @Override
  public void validate(OrderValidationContext context) {
    List<CartItemVO> items = context.getSnapshot().getItems();
    if (items == null || items.isEmpty() || items.size() > 100) {
      throw new OrderRejectedException("INVALID_ITEMS", "购买商品数量不合法");
    }
    var seen = new HashSet<Long>();
    for (CartItemVO item : items) {
      if (item.getSkuId() <= 0
          || item.getQuantity() < 1
          || item.getQuantity() > 20
          || !seen.add(item.getSkuId())) {
        throw new OrderRejectedException("INVALID_ITEMS", "商品规格重复或购买数量不合法");
      }
    }
  }
}
