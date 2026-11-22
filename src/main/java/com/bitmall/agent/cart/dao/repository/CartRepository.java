package com.bitmall.agent.cart.dao.repository;

import com.bitmall.agent.cart.dao.mapper.CartMapper;
import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 购物车表的数据访问组件。 联表读取可见商品、更新用户自己的条目；购买数量、库存和业务上限由 CartService 校验。 */
@Repository
@RequiredArgsConstructor
public class CartRepository {
  /** 购物车的 SQL，定义在 mapper/cart/CartMapper.xml。 */
  private final CartMapper mapper;

  /** 联表读取当前用户购物车、商品和规格，过滤已经下架的项目。 */
  public List<CartItemVO> listCart(long userId) {
    return mapper.listCartSql(parameters("userId", userId)).stream()
        .map(
            row ->
                new CartItemVO(
                    RowValues.longValue(row, "id"),
                    RowValues.longValue(row, "sku_id"),
                    RowValues.longValue(row, "product_id"),
                    RowValues.string(row, "product_name"),
                    RowValues.string(row, "spec"),
                    RowValues.string(row, "image_key"),
                    RowValues.decimal(row, "price"),
                    RowValues.intValue(row, "stock"),
                    RowValues.intValue(row, "quantity"),
                    RowValues.bool(row, "selected")))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** 已有同规格项则增加数量，没有则插入；并发插入冲突时转为增加数量。 */
  public void addCartItem(long userId, long skuId, int quantity) {
    int updated =
        mapper.incrementItemQuantity(
            parameters("quantity", quantity, "userId", userId, "skuId", skuId));
    if (updated == 0) {
      try {
        mapper.insertCartItem(parameters("userId", userId, "skuId", skuId, "quantity", quantity));
      } catch (DuplicateKeyException concurrentInsert) {
        mapper.incrementItemQuantity(
            parameters("quantity", quantity, "userId", userId, "skuId", skuId));
      }
    }
  }

  /** 按用户和条目编号更新数量及勾选状态，影响行数不符时拒绝。 */
  public void updateCartItem(long userId, long itemId, int quantity, Boolean selected) {
    int updated =
        mapper.updateCartItemSql(
            parameters(
                "quantity", quantity, "selected", selected, "itemId", itemId, "userId", userId));
    if (updated != 1) {
      throw new IllegalArgumentException("购物车商品不存在");
    }
  }

  /** 只删除属于当前用户的条目，防止通过猜编号修改别人的购物车。 */
  public void removeCartItem(long userId, long itemId) {
    int updated = mapper.removeCartItemSql(parameters("itemId", itemId, "userId", userId));
    if (updated != 1) {
      throw new IllegalArgumentException("购物车商品不存在");
    }
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
