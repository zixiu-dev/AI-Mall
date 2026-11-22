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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 订单交易的数据访问组件：订单、明细、库存和用户券相关 SQL。
 *
 * <p>本类不开启事务。一次建单/支付/关单包含多次写入，必须由 OrderCreationService、OrderLifecycleService 或 Agent
 * 确认执行的外层事务包住，任何一步失败都整体回滚。
 */
@Repository
@RequiredArgsConstructor
public class OrderRepository {
  /** 订单列表单次最多返回的条数。 */
  private static final int MAX_LIST_LIMIT = 20;

  /** 订单的 SQL，定义在 mapper/order/OrderMapper.xml。 */
  private final OrderMapper mapper;

  /** 与建单同一事务把订单关联到 trace；唯一索引保证一个 trace 最多生成一笔订单。 */
  public void attachTrace(String orderId, String traceId) {
    mapper.attachTraceSql(parameters("trace", traceId, "id", orderId));
  }

  /** 只查询当前用户最近订单，条数限制在 1 到 20 之间；表头、明细、物流组合成页面结果。 */
  public List<OrderDetailVO> listOrders(long userId, int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
    return mapper.listOrdersSql(parameters("userId", userId, "limit", boundedLimit)).stream()
        .map(this::mapOrder)
        .toList();
  }

  /** 按订单归属查询，查不到或不属于该用户时返回空，不向其他用户泄露订单。 */
  public Optional<OrderDetailVO> findOrder(long userId, String orderId) {
    return mapper.findOrderSql(parameters("userId", userId, "orderId", orderId)).stream()
        .findFirst()
        .map(this::mapOrder);
  }

  private OrderDetailVO mapOrder(Map<String, Object> row) {
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
            .map(this::mapOrderItem)
            .collect(Collectors.toCollection(ArrayList::new));
    List<LogisticsEventVO> events = logistics(id);
    LocalDateTime paymentExpiresAt = deadline == null ? null : deadline.toLocalDateTime();
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
        paymentExpiresAt,
        items,
        events);
  }

  private OrderItemVO mapOrderItem(Map<String, Object> row) {
    return new OrderItemVO(
        RowValues.longValue(row, "sku_id"),
        RowValues.string(row, "product_name"),
        RowValues.string(row, "sku_spec"),
        RowValues.decimal(row, "unit_price"),
        RowValues.intValue(row, "quantity"));
  }

  /** 按时间倒序读取指定订单的物流事件；本方法不校验归属，对外调用前由订单服务核对。 */
  public List<LogisticsEventVO> logistics(String orderId) {
    return mapper.logisticsSql(parameters("orderId", orderId)).stream()
        .map(
            row ->
                new LogisticsEventVO(
                    RowValues.timestamp(row, "event_time").toLocalDateTime(),
                    RowValues.string(row, "status"),
                    RowValues.string(row, "description")))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * 写入一笔新订单：扣库存、写订单主表和明细、占用用户券、清理已结算购物车。
   *
   * <p>任何一步不满足条件都会抛出异常，由外层服务事务整体回滚，不会留下"扣了库存却没有订单"的中间状态。
   */
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
    deductStockInSkuOrder(items);
    insertOrderHeader(
        orderId, userId, goodsAmount, discount, freight, payAmount, couponId, couponName, address,
        deadline);
    insertOrderItems(orderId, items);
    if (userCouponId != null) {
      occupyAvailableCoupon(userCouponId, userId, orderId);
    }
    if (clearCart) {
      deleteSettledCartItems(userId, items);
    }
    return orderId;
  }

  /**
   * 第 1 步：用条件更新扣库存，SQL 同时核对库存充足、价格未变、仍在上架，并发请求不会把库存扣成负数。
   *
   * <p>多个 SKU 按编号从小到大依次加锁，两个购物车交叉下单时加锁顺序一致，减少死锁。任何一项不满足都抛异常回滚，
   * 要求用户重新确认。
   */
  private void deductStockInSkuOrder(List<CartItemVO> items) {
    List<CartItemVO> itemsInSkuOrder =
        items.stream().sorted(Comparator.comparingLong(CartItemVO::getSkuId)).toList();
    for (CartItemVO item : itemsInSkuOrder) {
      int updated = mapper.deductSkuStock(item.getSkuId(), item.getQuantity(), item.getPrice());
      if (updated != 1) {
        throw new OrderRejectedException(
            "STOCK_CHANGED", item.getProductName() + " 库存、价格或上架状态已经变化，请重新结算");
      }
    }
  }

  /** 第 2 步：保存订单主表及金额、收货地址快照；用了优惠券时一并保存券快照。 */
  private void insertOrderHeader(
      String orderId,
      long userId,
      BigDecimal goodsAmount,
      BigDecimal discount,
      BigDecimal freight,
      BigDecimal payAmount,
      Long couponId,
      String couponName,
      String address,
      LocalDateTime deadline) {
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
  }

  /** 第 2 步（续）：按购物车原顺序保存商品明细。 */
  private void insertOrderItems(String orderId, List<CartItemVO> items) {
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
  }

  /** 第 3 步：只占用仍为 AVAILABLE 的用户券；券已被用掉或状态变化时抛异常，外层事务整体回滚。 */
  private void occupyAvailableCoupon(Long userCouponId, long userId, String orderId) {
    int used = mapper.useAvailableCoupon(userCouponId, userId, orderId);
    if (used != 1) {
      throw new OrderRejectedException("COUPON_CHANGED", "优惠券状态已经变化，请重新结算");
    }
  }

  /** 第 4 步：只清理本次已经结算的购物车项，未勾选的项继续保留。 */
  private void deleteSettledCartItems(long userId, List<CartItemVO> items) {
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

  /**
   * 对订单加行锁并读取（SELECT ... FOR UPDATE）。支付、取消、超时关单都先调用它，锁住后才判断状态。
   *
   * <p>必须直接使用这次锁定读取的结果：MySQL 可重复读隔离级别下，锁后再做普通查询可能读到事务开始时的旧快照。
   */
  public OrderDetailVO lockOrder(long userId, String orderId) {
    return mapper.lockOrderSql(parameters("orderId", orderId, "userId", userId)).stream()
        .findFirst()
        .map(this::mapOrder)
        .orElseThrow(() -> new IllegalArgumentException("订单不存在或不属于当前用户"));
  }

  /** 把待支付订单改为指定关闭状态；返回 false 表示订单已不是待支付。是否超时由服务在锁住订单后判断。 */
  public boolean closeOrder(long userId, String orderId, String status) {
    int changed =
        mapper.cancelEligibleOrder(parameters("orderId", orderId, "userId", userId, "status", status));
    return changed == 1;
  }

  /** 只把仍在支付有效期内的待支付订单改为已支付；重复支付返回 false，不会再次处理库存。 */
  public boolean markPaid(long userId, String orderId) {
    int changed = mapper.payPendingOrder(parameters("orderId", orderId, "userId", userId));
    return changed == 1;
  }

  /**
   * 订单状态变更成功后处理数据库中的锁定库存：支付则确认扣减，关单则返还可售。
   *
   * <p>按 SKU 顺序加锁以减少死锁；任何一行更新数量不为 1 都说明库存账不一致，抛异常回滚并留待人工核对。
   */
  public void finishStock(OrderDetailVO order, boolean paid) {
    List<OrderItemVO> itemsInSkuOrder =
        order.getItems().stream().sorted(Comparator.comparingLong(OrderItemVO::getSkuId)).toList();
    for (OrderItemVO item : itemsInSkuOrder) {
      Map<String, Object> args = parameters("quantity", item.getQuantity(), "skuId", item.getSkuId());
      int changed = paid ? mapper.confirmSkuStock(args) : mapper.returnSkuStock(args);
      if (changed != 1) {
        throw new IllegalStateException("订单锁定库存不一致，请核对订单 " + order.getId());
      }
    }
  }

  /**
   * 处理订单锁定的优惠券：支付则核销，关单则恢复可用。
   *
   * <p>同一订单的锁定券只会被处理一次。订单用过券（有券名快照）却没有恰好处理一张券时，说明数据不一致，抛异常回滚。
   */
  public void finishCoupon(OrderDetailVO order, boolean paid) {
    Map<String, Object> args = parameters("orderId", order.getId(), "userId", order.getUserId());
    int changed = paid ? mapper.consumeLockedCoupons(args) : mapper.restoreOrderCoupons(args);
    boolean orderUsedCoupon = order.getCouponName() != null;
    if (orderUsedCoupon && changed != 1) {
      throw new IllegalStateException("订单锁定优惠券不一致，请核对订单 " + order.getId());
    }
  }

  /** 后台分批扫描到期订单；服务会对每一笔单独开启事务并在锁住后重新核对状态。 */
  public List<Map<String, Object>> expiredOrders() {
    return mapper.expiredOrders(Map.of());
  }

  /** 把 "键1, 值1, 键2, 值2..." 形式的参数组装成 Mapper 参数 Map；值保持原类型，允许为 null。 */
  private static Map<String, Object> parameters(Object... keyValuePairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      String key = (String) keyValuePairs[i];
      Object value = keyValuePairs[i + 1];
      values.put(key, value);
    }
    return values;
  }
}
