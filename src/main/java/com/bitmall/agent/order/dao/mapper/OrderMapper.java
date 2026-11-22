package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
  int attachTraceSql(Map<String, Object> values);

  List<Map<String, Object>> listOrdersSql(Map<String, Object> values);

  List<Map<String, Object>> findOrderSql(Map<String, Object> values);

  List<Map<String, Object>> selectOrderItems(
      Map<String, Object> values);

  List<Map<String, Object>> logisticsSql(Map<String, Object> values);

  /** 有库存才扣减，返回影响行数；事务由上层服务控制。 */
  int deductSkuStock(
      @Param("skuId") long skuId,
      @Param("quantity") int quantity,
      @Param("expectedPrice") BigDecimal expectedPrice);

  int insertOrderWithoutCoupon(Map<String, Object> values);

  int insertOrderWithCoupon(Map<String, Object> values);

  int insertOrderItem(Map<String, Object> values);

  /** 当前用户的券仍可用时才占用并绑定订单。 */
  int useAvailableCoupon(
      @Param("userCouponId") long userCouponId,
      @Param("userId") long userId,
      @Param("orderId") String orderId);

  int deleteSettledCartItem(Map<String, Object> values);

  int cancelEligibleOrder(Map<String, Object> values);

  int returnSkuStock(Map<String, Object> values);

  int restoreOrderCoupons(Map<String, Object> values);

  /** 支付、取消和超时关闭都先锁同一订单，再检查状态。 */
  List<Map<String, Object>> lockOrderSql(Map<String, Object> values);

  int payPendingOrder(Map<String, Object> values);

  int confirmSkuStock(Map<String, Object> values);

  int consumeLockedCoupons(Map<String, Object> values);

  List<Map<String, Object>> expiredOrders(Map<String, Object> values);
}
