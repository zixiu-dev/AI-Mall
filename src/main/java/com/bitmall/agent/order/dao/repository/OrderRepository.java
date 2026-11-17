package com.bitmall.agent.order.dao.repository;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.mapper.OrderMapper;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.vo.LogisticsEventVO;
import com.bitmall.agent.order.vo.OrderDetailVO;
import com.bitmall.agent.order.vo.OrderItemVO;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 订单交易的数据访问组件。 执行订单、明细、库存和用户券的相关 SQL；多次写入必须由 OrderService 或确认执行的外层事务包住。 */
@Repository
public class OrderRepository {
  /** 与建单同事务关联 trace，唯一索引阻止一个 trace 生成两笔订单。 */
  public void attachTrace(String orderId, String traceId) {
    mapper.attachTraceSql(parameters("trace", traceId, "id", orderId));
  }

  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final OrderMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public OrderRepository(OrderMapper mapper) {
    this.mapper = mapper;
  }

  /** 只查询当前用户最近订单；表头、明细、物流组合成页面结果。 */
  public List<OrderDetailVO> listOrders(long userId, int limit) {
    return mapper
        .listOrdersSql(parameters("userId", userId, "limit", Math.max(1, Math.min(limit, 20))))
        .stream()
        .map(this::mapOrder)
        .toList();
  }

  /** 按归属查询订单，不向其他用户泄露订单。 */
  public Optional<OrderDetailVO> findOrder(long userId, String orderId) {
    return mapper.findOrderSql(parameters("userId", userId, "orderId", orderId)).stream()
        .findFirst()
        .map(this::mapOrder);
  }

  private OrderDetailVO mapOrder(java.util.Map<String, Object> row) {
    return mapOrderRow(
        RowValues.string(row, "id"),
        RowValues.longValue(row, "user_id"),
        RowValues.string(row, "status"),
        RowValues.decimal(row, "goods_amount"),
        RowValues.decimal(row, "discount_amount"),
        RowValues.decimal(row, "freight_amount"),
        RowValues.decimal(row, "pay_amount"),
        RowValues.string(row, "coupon_name"),
        RowValues.string(row, "address_snapshot"),
        RowValues.timestamp(row, "created_at"),
        RowValues.timestamp(row, "payment_expires_at"));
  }

  /** 把订单主表的一行与商品明细、物流轨迹组合成 OrderDetailVO。 */
  private OrderDetailVO mapOrderRow(
      String id,
      long userId,
      String status,
      BigDecimal goods,
      BigDecimal discount,
      BigDecimal freight,
      BigDecimal pay,
      String couponName,
      String address,
      Timestamp createdAt,
      Timestamp deadline) {
    List<OrderItemVO> items =
        mapper.selectOrderItems(parameters("orderId", id)).stream()
            .map(
                row ->
                    new OrderItemVO(
                        RowValues.longValue(row, "sku_id"),
                        RowValues.string(row, "product_name"),
                        RowValues.string(row, "sku_spec"),
                        RowValues.decimal(row, "unit_price"),
                        RowValues.intValue(row, "quantity")))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    List<LogisticsEventVO> events = logistics(id);
    return new OrderDetailVO(
        id,
        userId,
        status,
        goods,
        discount,
        freight,
        pay,
        couponName,
        address,
        createdAt.toLocalDateTime(),
        deadline == null ? null : deadline.toLocalDateTime(),
        items,
        events);
  }

  /** 按时间倒序读取指定订单的物流事件；对外调用前由订单服务核对归属。 */
  public List<LogisticsEventVO> logistics(String orderId) {
    return mapper.logisticsSql(parameters("orderId", orderId)).stream()
        .map(
            row ->
                new LogisticsEventVO(
                    RowValues.timestamp(row, "event_time").toLocalDateTime(),
                    RowValues.string(row, "status"),
                    RowValues.string(row, "description")))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
  }

  /** 执行条件扣库存、写订单和明细、占用用户券、清理已结算购物车；由外层服务事务保证原子性。 */
  public String createOrder(
      String orderId,
      long userId,
      List<CartItemVO> items,
      BigDecimal goodsAmount,
      BigDecimal discount,
      BigDecimal freight,
      BigDecimal payAmount,
      Long userCouponId,
      Long couponId,
      String couponName,
      String address,
      boolean clearCart,
      LocalDateTime deadline) {
    // 1. 用 stock >= quantity 的条件更新扣库存；并发请求不能把库存扣成负数。
    for (CartItemVO item :
        items.stream().sorted(java.util.Comparator.comparingLong(CartItemVO::getSkuId)).toList()) {
      // 多 SKU 按编号顺序占用，减少购物车交叉下单的死锁。
      // 同时核对价格与可售数量，任何一项变化均回滚并要求重新确认。
      int updated = mapper.deductSkuStock(item.getSkuId(), item.getQuantity(), item.getPrice());
      if (updated != 1)
        throw new OrderRejectedException(
            "STOCK_CHANGED", item.getProductName() + " 库存、价格或上架状态已经变化，请重新结算");
    }

    // 2. 保存订单主表及金额、收货地址、优惠券快照，再保存商品明细。
    if (couponId == null) {
      mapper.insertOrderWithoutCoupon(
          parameters(
              "id",
              orderId,
              "userId",
              userId,
              "goods",
              goodsAmount,
              "discount",
              discount,
              "freight",
              freight,
              "pay",
              payAmount,
              "address",
              address,
              "deadline",
              deadline));
    } else {
      mapper.insertOrderWithCoupon(
          parameters(
              "id",
              orderId,
              "userId",
              userId,
              "goods",
              goodsAmount,
              "discount",
              discount,
              "freight",
              freight,
              "pay",
              payAmount,
              "couponId",
              couponId,
              "couponName",
              couponName,
              "address",
              address,
              "deadline",
              deadline));
    }

    for (CartItemVO item : items) {
      mapper.insertOrderItem(
          parameters(
              "orderId",
              orderId,
              "productId",
              item.getProductId(),
              "skuId",
              item.getSkuId(),
              "name",
              item.getProductName(),
              "spec",
              item.getSkuSpec(),
              "price",
              item.getPrice(),
              "quantity",
              item.getQuantity()));
    }

    // 3. 条件占用仍为 AVAILABLE 的用户券；券状态变化会抛异常，外层事务整体回滚。
    if (userCouponId != null) {
      int used = mapper.useAvailableCoupon(userCouponId, userId, orderId);
      if (used != 1) throw new OrderRejectedException("COUPON_CHANGED", "优惠券状态已经变化，请重新结算");
    }
    // 4. 仅清理本次已经结算的购物车项，未勾选项继续保留。
    if (clearCart) {
      for (CartItemVO item : items) {
        mapper.deleteSettledCartItem(
            parameters(
                "cartId",
                item.getId(),
                "quantity",
                item.getQuantity(),
                "userId",
                userId,
                "skuId",
                item.getSkuId()));
      }
    }
    return orderId;
  }

  /** 支付、取消共用订单行锁；锁住后才读取状态和明细，不能依赖锁前的查询结果。 */
  public OrderDetailVO lockOrder(long userId, String orderId) {
    // 直接使用锁定读取的表头；MySQL RR 下再次普通查询可能落回旧快照。
    return mapper.lockOrderSql(parameters("orderId", orderId, "userId", userId)).stream()
        .findFirst()
        .map(this::mapOrder)
        .orElseThrow(() -> new IllegalArgumentException("订单不存在或不属于当前用户"));
  }

  /** 只有待支付订单能关单。是否超时由事务服务在锁住订单后判断。 */
  public boolean closeOrder(long userId, String orderId, String status) {
    return mapper.cancelEligibleOrder(
            parameters("orderId", orderId, "userId", userId, "status", status))
        == 1;
  }

  /** 仅确认有效期内的待支付订单，重复支付不再次修改库存。 */
  public boolean markPaid(long userId, String orderId) {
    return mapper.payPendingOrder(parameters("orderId", orderId, "userId", userId)) == 1;
  }

  /** 订单状态变更成功后才处理锁定库存；任何数量不匹配都回滚并留下异常供核对。 */
  public void finishStock(OrderDetailVO order, boolean paid) {
    for (var item :
        order.getItems().stream()
            .sorted(java.util.Comparator.comparingLong(OrderItemVO::getSkuId))
            .toList()) {
      var args = parameters("quantity", item.getQuantity(), "skuId", item.getSkuId());
      int changed = paid ? mapper.confirmSkuStock(args) : mapper.returnSkuStock(args);
      if (changed != 1) throw new IllegalStateException("订单锁定库存不一致，请核对订单 " + order.getId());
    }
  }

  /** 同一订单绑定的锁定券只会核销或释放一次；曾用券的订单必须确实处理一张锁定券。 */
  public void finishCoupon(OrderDetailVO order, boolean paid) {
    var args = parameters("orderId", order.getId(), "userId", order.getUserId());
    int changed = paid ? mapper.consumeLockedCoupons(args) : mapper.restoreOrderCoupons(args);
    if (order.getCouponName() != null && changed != 1)
      throw new IllegalStateException("订单锁定优惠券不一致，请核对订单 " + order.getId());
  }

  /** 后台分批扫描到期订单，服务会逐笔开启事务并重新核对状态。 */
  public List<java.util.Map<String, Object>> expiredOrders() {
    return mapper.expiredOrders(java.util.Map.of());
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
