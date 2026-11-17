package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
  int attachTraceSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> listOrdersSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findOrderSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectOrderItems(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> logisticsSql(java.util.Map<String, Object> values);

  /** 有库存才扣减，返回影响行数；事务由上层服务控制。 */
  int deductSkuStock(
      @Param("skuId") long skuId,
      @Param("quantity") int quantity,
      @Param("expectedPrice") java.math.BigDecimal expectedPrice);

  int insertOrderWithoutCoupon(java.util.Map<String, Object> values);

  int insertOrderWithCoupon(java.util.Map<String, Object> values);

  int insertOrderItem(java.util.Map<String, Object> values);

  /** 当前用户的券仍可用时才占用并绑定订单。 */
  int useAvailableCoupon(
      @Param("userCouponId") long userCouponId,
      @Param("userId") long userId,
      @Param("orderId") String orderId);

  int deleteSettledCartItem(java.util.Map<String, Object> values);

  int cancelEligibleOrder(java.util.Map<String, Object> values);

  int returnSkuStock(java.util.Map<String, Object> values);

  int restoreOrderCoupons(java.util.Map<String, Object> values);

  /** 支付、取消和超时关闭都先锁同一订单，再检查状态。 */
  java.util.List<java.util.Map<String, Object>> lockOrderSql(java.util.Map<String, Object> values);

  int payPendingOrder(java.util.Map<String, Object> values);

  int confirmSkuStock(java.util.Map<String, Object> values);

  int consumeLockedCoupons(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> expiredOrders(java.util.Map<String, Object> values);
}
