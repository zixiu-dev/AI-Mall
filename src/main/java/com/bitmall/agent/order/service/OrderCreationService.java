package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.inventory.InventorySettlementEvent;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.monitor.OrderMetrics;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.validation.OrderValidationChain;
import com.bitmall.agent.product.cache.CatalogChanged;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 同步和异步共用的建单事务边界：库存、券、订单和 trace 成功状态一起提交或回滚。 */
@Service
public class OrderCreationService {
  /** trace 行锁和条件更新是分布式锁失效后的兜底。 */
  private final OrderTraceRepository traces;

  /** 订单、库存、用户券的数据访问。 */
  private final OrderRepository orders;

  /** 购买快照及回执的序列化。 */
  private final OrderSnapshotService snapshots;

  /** 按顺序组织业务检查。 */
  private final OrderValidationChain validation;

  /** 可靠消息记录，交易事件与建单命令分开。 */
  private final OrderMessageService messages;

  /** 事务提交后使商品展示缓存失效。 */
  private final ApplicationEventPublisher events;

  /** 只统计已经提交的结果。 */
  private final OrderMetrics metrics;

  /** 支付有效期等业务参数。 */
  private final OrderProcessingProperties properties;

  private final OrderInventoryService inventory;

  /** 注入业务组件；本服务不调用模型、不发送 MQ、不依赖活动库存池。 */
  public OrderCreationService(
      OrderTraceRepository traces,
      OrderRepository orders,
      OrderSnapshotService snapshots,
      OrderValidationChain validation,
      OrderMessageService messages,
      ApplicationEventPublisher events,
      OrderMetrics metrics,
      OrderProcessingProperties properties,
      OrderInventoryService inventory) {
    this.traces = traces;
    this.orders = orders;
    this.snapshots = snapshots;
    this.validation = validation;
    this.messages = messages;
    this.events = events;
    this.metrics = metrics;
    this.properties = properties;
    this.inventory = inventory;
  }

  /** 事务提交后才返回成功；Agent 确认调用时加入它的外层事务。 */
  @Transactional
  public void create(long user, String id) {
    var trace = traces.lock(id);
    if (trace.getUserId() != user) throw new IllegalArgumentException("下单任务不存在或不属于当前用户");
    // 已成功或明确失败的请求只读原结果，不会再次占用库存。
    if (!"INIT".equals(trace.getStatus())) {
      metrics.event("repeat_submit");
      return;
    }
    if (!trace.getExpiresAt().isAfter(LocalDateTime.now()))
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    if (!"SYNC".equals(trace.getMode())) throw new IllegalArgumentException("排队购买必须通过异步受理入口提交");
    if (!traces.claim(trace)) return;
    trace = traces.lock(id); // CAS 已递增版本，成功更新必须使用新版本。
    build(trace);
  }

  /** 消费者只处理已可靠受理的任务；重复消息读取终态，未提交的 INIT 不能被消息直接建单。 */
  @Transactional
  public void createQueued(String id) {
    var trace = traces.lock(id);
    if (java.util.Set.of("SUCCESS", "FAILED", "EXPIRED").contains(trace.getStatus())) {
      metrics.event("repeat_consume");
      return;
    }
    if (!"ASYNC".equals(trace.getMode()) || !"PROCESSING".equals(trace.getStatus()))
      throw new IllegalStateException("购买任务尚未可靠受理，不能由消息直接创建订单");
    if (trace.getProcessingExpiresAt() == null
        || !trace.getProcessingExpiresAt().isAfter(LocalDateTime.now()))
      throw new OrderRejectedException("QUEUE_TIMEOUT", "排队处理超时，本次未创建订单，请重新确认购买");
    build(trace);
  }

  /** 两种入口只改变执行时机，库存、券、价格、购物车与订单写入规则完全共用。 */
  private void build(OrderTraceDO trace) {
    long user = trace.getUserId();
    String id = trace.getTraceId();
    var quote = validation.validate(user, trace);
    inventory.requireReserved(trace, quote);
    var coupon = quote.getCoupon();
    String orderId = "WW" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    var deadline = LocalDateTime.now().plusSeconds(properties.getPaymentTtlSeconds());
    orders.createOrder(
        orderId,
        trace.getUserId(),
        quote.getItems(),
        quote.getGoodsAmount(),
        quote.getDiscountAmount(),
        quote.getFreightAmount(),
        quote.getPayAmount(),
        coupon == null ? null : coupon.getUserCouponId(),
        coupon == null ? null : coupon.getId(),
        coupon == null ? null : coupon.getName(),
        quote.getAddress().display(),
        "CART".equals(quote.getSource()),
        deadline);
    orders.attachTrace(orderId, id);
    var result = new LinkedHashMap<String, Object>();
    result.put("success", true);
    result.put("orderId", orderId);
    result.put("status", "PENDING_PAYMENT");
    result.put("payAmount", quote.getPayAmount());
    result.put("discountAmount", quote.getDiscountAmount());
    result.put("paymentExpiresAt", deadline);
    result.put("traceId", id);
    result.put("submitMode", trace.getMode());
    result.put("message", "订单已创建，请在有效期内支付");
    traces.succeed(trace, orderId, snapshots.write(result));
    metrics.committed("created", trace.getMode());
    metrics.completed(trace.getMode(), trace.getAcceptedAt());
    messages.enqueue(OrderEventMessage.CREATED, orderId, user, quote.getPayAmount());
    // 与订单同事务保存定时关单意图；提交后才由 Outbox 向 Broker 发送。
    messages.enqueuePaymentTimeout(orderId, user, deadline);
    events.publishEvent(new CatalogChanged(quote.getItems().stream().map(item -> item.getSkuId()).toList()));
    events.publishEvent(new InventorySettlementEvent(id, null));
  }
}
