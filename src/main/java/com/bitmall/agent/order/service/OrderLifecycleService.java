package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.inventory.InventorySettlementEvent;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.vo.OrderDetailVO;
import com.bitmall.agent.product.cache.CatalogChanged;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 待支付订单的支付、取消和超时关单。
 *
 * <p>三个入口都先锁住订单行再判断状态，所以并发的"支付"和"关单"只会有一个成功。
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {
  private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
  private static final String STATUS_PAID = "PAID";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String STATUS_CLOSED = "CLOSED";

  private static final String TIMEOUT_CLOSE_MESSAGE = "订单支付超时，已关闭并释放占用";

  /** 锁定订单行，更新订单状态，并结算数据库锁定库存和锁定优惠券。 */
  private final OrderRepository orders;

  /** 在同一事务里写入"订单已支付"/"订单已取消" Outbox 消息，事务提交后才投递。 */
  private final OrderMessageService messages;

  /** 发布商品缓存失效和库存归还整理事件（事务提交后处理）。 */
  private final ApplicationEventPublisher events;

  /** 关单前为旧订单补登 Redis 预占记录；关单时在同一事务里写入 Redis 可售归还任务（提交后由后台执行）。 */
  private final OrderInventoryService inventory;

  /**
   * 演示支付：只由显式启用的 Demo Controller 调用，不是真实支付回调。
   *
   * <p>重复支付返回原结果；锁住订单时已经超时的，直接按超时关单处理。
   */
  @Transactional
  public Map<String, Object> payDemo(long userId, String orderId) {
    OrderDetailVO order = orders.lockOrder(userId, orderId);
    if (STATUS_PAID.equals(order.getStatus())) {
      return result(order, STATUS_PAID, "订单已经支付，请勿重复支付");
    }
    if (!STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
      throw new IllegalStateException("订单已关闭或不处于待支付状态，不能支付");
    }
    if (expired(order)) {
      return close(order, STATUS_CLOSED, TIMEOUT_CLOSE_MESSAGE);
    }
    // markPaid 的 SQL 会再次检查状态和支付期限，返回 false 说明期间被关单或刚好超时。
    if (!orders.markPaid(userId, orderId)) {
      throw new IllegalStateException("支付有效期或订单状态已变化，请刷新订单");
    }
    // 可售库存在下单时已经扣过；支付这里只清掉锁定量、核销锁定的券，不再重复扣库存。
    orders.finishStock(order, true);
    orders.finishCoupon(order, true);
    messages.enqueue(OrderEventMessage.PAID, orderId, userId, order.getPayAmount());
    return result(order, STATUS_PAID, "演示支付成功，未发起真实扣款");
  }

  /**
   * 用户主动取消待支付订单。
   *
   * <p>重复取消返回原关闭结果，不会重复返还库存和券；已支付订单必须走售后。 取消时如果其实已经超时，状态记为 CLOSED 而不是 CANCELLED。
   */
  @Transactional
  public Map<String, Object> cancel(long userId, String orderId) {
    OrderDetailVO order = orders.lockOrder(userId, orderId);
    boolean alreadyClosed =
        STATUS_CANCELLED.equals(order.getStatus()) || STATUS_CLOSED.equals(order.getStatus());
    if (alreadyClosed) {
      return result(order, order.getStatus(), "订单已经关闭，库存和优惠券不会重复返还");
    }
    if (!STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
      throw new com.bitmall.agent.common.exception.BusinessRejectedException("只有待支付订单可以直接取消，已支付订单请申请售后");
    }
    String closedStatus = expired(order) ? STATUS_CLOSED : STATUS_CANCELLED;
    return close(order, closedStatus, "订单已关闭，库存和优惠券已释放");
  }

  /**
   * 超时关单，由延迟 MQ 消息和兜底扫描任务共用。
   *
   * @return true 表示这笔订单已处理完（包括已支付、已关闭的情况）；false 表示还没到期，调用方应稍后重试（延迟消息可能提前到达）
   */
  @Transactional
  public boolean closeExpired(long userId, String orderId) {
    OrderDetailVO order = orders.lockOrder(userId, orderId);
    if (!STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
      return true;
    }
    if (!expired(order)) {
      return false;
    }
    close(order, STATUS_CLOSED, TIMEOUT_CLOSE_MESSAGE);
    return true;
  }

  /** 关闭订单并释放库存和券。调用方必须已经在当前事务里锁住订单行，所有释放动作与状态变更一起提交。 */
  private Map<String, Object> close(OrderDetailVO order, String status, String message) {
    inventory.beforeClose(order);
    if (!orders.closeOrder(order.getUserId(), order.getId(), status)) {
      throw new com.bitmall.agent.common.exception.BusinessRejectedException("订单状态已变化，请刷新订单");
    }
    orders.finishStock(order, false);
    orders.finishCoupon(order, false);
    inventory.recordReturn(order);
    messages.enqueue(
        OrderEventMessage.CANCELLED, order.getId(), order.getUserId(), order.getPayAmount());
    List<Long> skuIds = order.getItems().stream().map(item -> item.getSkuId()).toList();
    events.publishEvent(new CatalogChanged(skuIds));
    events.publishEvent(new InventorySettlementEvent(null, order.getId()));
    return result(order, status, message);
  }

  /** 没有支付期限的旧订单按已过期处理。 */
  private boolean expired(OrderDetailVO order) {
    LocalDateTime paymentDeadline = order.getPaymentExpiresAt();
    return paymentDeadline == null || !paymentDeadline.isAfter(LocalDateTime.now());
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
