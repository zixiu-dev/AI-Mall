package com.bitmall.agent.cart.service;

import com.bitmall.agent.cart.dao.repository.CartRepository;
import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 管理购物车业务。 检查购买数量、商品可用性、库存和单项上限，再调用 CartRepository 更新；结算金额不是在这里最终确定的。 */
@Service
public class CartService {
  /** 依赖组件：购物车表的数据访问组件。 */
  private final CartRepository mapper;

  /** 依赖组件：商品和规格的基础数据访问组件。 */
  private final ProductRepository products;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public CartService(CartRepository mapper, ProductRepository products) {
    this.mapper = mapper;
    this.products = products;
  }

  /** 查询购物车，汇总商品件数和勾选项金额。 */
  public Map<String, Object> cart(long userId) {
    List<CartItemVO> items = mapper.listCart(userId);
    BigDecimal selectedAmount =
        items.stream()
            .filter(CartItemVO::isSelected)
            .map(CartItemVO::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", items);
    result.put("itemCount", items.stream().mapToInt(CartItemVO::getQuantity).sum());
    result.put("selectedAmount", selectedAmount.setScale(2, RoundingMode.HALF_UP));
    return result;
  }

  /** 检查数量、上下架、库存和累计上限，通过后加入并返回最新购物车。 */
  public Map<String, Object> addCartItem(long userId, long skuId, int quantity) {
    validateQuantity(quantity);
    CartItemVO item =
        products
            .findPurchaseItem(skuId, quantity)
            .orElseThrow(() -> new IllegalArgumentException("商品规格不存在或已经下架"));
    int existing =
        mapper.listCart(userId).stream()
            .filter(value -> value.getSkuId() == skuId)
            .mapToInt(CartItemVO::getQuantity)
            .findFirst()
            .orElse(0);
    if (existing + quantity > item.getStock())
      throw new IllegalStateException("库存不足，当前仅剩 " + item.getStock() + " 件");
    if (existing + quantity > 20) throw new IllegalArgumentException("同一商品最多购买 20 件");
    mapper.addCartItem(userId, skuId, quantity);
    return cart(userId);
  }

  /** 先核对条目归属与库存，再修改数量或勾选状态。 */
  public Map<String, Object> updateCartItem(
      long userId, long itemId, int quantity, Boolean selected) {
    validateQuantity(quantity);
    CartItemVO item =
        mapper.listCart(userId).stream()
            .filter(value -> value.getId() == itemId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("购物车商品不存在"));
    if (quantity > item.getStock())
      throw new IllegalStateException("库存不足，当前仅剩 " + item.getStock() + " 件");
    mapper.updateCartItem(userId, itemId, quantity, selected);
    return cart(userId);
  }

  /** 删除当前用户的条目后重新读取购物车。 */
  public Map<String, Object> removeCartItem(long userId, long itemId) {
    mapper.removeCartItem(userId, itemId);
    return cart(userId);
  }

  /** 统一限制单项购买数量在 1 到 20 之间。 */
  private static void validateQuantity(int quantity) {
    if (quantity < 1 || quantity > 20) throw new IllegalArgumentException("购买数量必须在 1 到 20 之间");
  }
}
