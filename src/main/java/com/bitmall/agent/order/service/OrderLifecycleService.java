package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.inventory.InventorySettlementEvent;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.vo.OrderDetailVO;
import com.bitmall.agent.product.cache.CatalogChanged;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 待支付订单的支付与关闭事务。它们争抢同一订单行锁，只允许一个合法状态转换成功。 */
@Service
public class OrderLifecycleService {
  /** 状态 CAS、库存和优惠券使用同一数据库。 */
  private final OrderRepository orders;

  /** 仅记录已经完成的订单变更。 */
  private final OrderMessageService messages;

  /** 提交成功后刷新展示缓存。 */
  private final ApplicationEventPublisher events;

  private final OrderInventoryService inventory;

  public OrderLifecycleService(
      OrderRepository orders,
      OrderMessageService messages,
      ApplicationEventPublisher events,
      OrderInventoryService inventory) {
    this.orders = orders;
    this.messages = messages;
    this.events = events;
    this.inventory = inventory;
  }

  /** 演示支付入口：只由显式启用的 Demo Controller 调用，不是外部支付回调接口。 */
  @Transactional
  public Map<String, Object> payDemo(long userId, String orderId) {
    var order = orders.lockOrder(userId, orderId);
    if ("PAID".equals(order.getStatus())) return result(order, "PAID", "订单已经支付，请勿重复支付");
    if (!"PENDING_PAYMENT".equals(order.getStatus()))
      throw new IllegalStateException("订单已关闭或不处于待支付状态，不能支付");
    if (expired(order)) return close(order, "CLOSED", "订单支付超时，已关闭并释放占用");
    if (!orders.markPaid(userId, orderId)) throw new IllegalStateException("支付有效期或订单状态已变化，请刷新订单");
    // 下单时已减可售；支付只消除锁定量、核销锁定券，不重复减库存。
    orders.finishStock(order, true);
    orders.finishCoupon(order, true);
    messages.enqueue(OrderEventMessage.PAID, orderId, userId, order.getPayAmount());
    events.publishEvent(new CatalogChanged());
    return result(order, "PAID", "演示支付成功，未发起真实扣款");
  }

  /** 重复取消返回原关闭结果；已支付订单须走售后，不直接返还库存。 */
  @Transactional
  public Map<String, Object> cancel(long userId, String orderId) {
    var order = orders.lockOrder(userId, orderId);
    if ("CANCELLED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus()))
      return result(order, order.getStatus(), "订单已经关闭，库存和优惠券不会重复返还");
    if (!"PENDING_PAYMENT".equals(order.getStatus()))
      throw new IllegalStateException("只有待支付订单可以直接取消，已支付订单请申请售后");
    return close(order, expired(order) ? "CLOSED" : "CANCELLED", "订单已关闭，库存和优惠券已释放");
  }

  /** MQ 和扫描共用关单事务。已支付/已关闭视为处理完成；未到期返回 false，让提前消息重试。 */
  @Transactional
  public boolean closeExpired(long userId, String orderId) {
    var order = orders.lockOrder(userId, orderId);
    if (!"PENDING_PAYMENT".equals(order.getStatus())) return true;
    if (!expired(order)) return false;
    close(order, "CLOSED", "订单支付超时，已关闭并释放占用");
    return true;
  }

  /** 只在已持有订单锁的事务中调用，所有释放动作与状态变更一起提交。 */
  private Map<String, Object> close(OrderDetailVO order, String status, String message) {
    inventory.beforeClose(order);
    if (!orders.closeOrder(order.getUserId(), order.getId(), status))
      throw new IllegalStateException("订单状态已变化，请刷新订单");
    orders.finishStock(order, false);
    orders.finishCoupon(order, false);
    inventory.recordReturn(order);
    messages.enqueue(
        OrderEventMessage.CANCELLED, order.getId(), order.getUserId(), order.getPayAmount());
    events.publishEvent(new CatalogChanged());
    events.publishEvent(new InventorySettlementEvent(null, order.getId()));
    return result(order, status, message);
  }

  private boolean expired(OrderDetailVO order) {
    return order.getPaymentExpiresAt() == null
        || !order.getPaymentExpiresAt().isAfter(LocalDateTime.now());
  }

  private Map<String, Object> result(OrderDetailVO order, String status, String message) {
    return Map.of(
        "success",
        true,
        "orderId",
        order.getId(),
        "status",
        status,
        "payAmount",
        order.getPayAmount(),
        "message",
        message);
  }
}
