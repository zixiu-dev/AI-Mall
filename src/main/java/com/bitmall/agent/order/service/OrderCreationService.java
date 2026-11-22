package com.bitmall.agent.order.service;

import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.coupon.vo.CouponVO;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 真正创建订单的事务服务，同步下单和异步消息消费都走这里。
 *
 * <p>库存扣减、优惠券占用、订单写入和 trace 标记成功在同一个事务里，要么全部提交，要么全部回滚。
 */
@Service
@RequiredArgsConstructor
public class OrderCreationService {
  private static final String STATUS_INIT = "INIT";
  private static final String STATUS_PROCESSING = "PROCESSING";
  private static final String MODE_SYNC = "SYNC";
  private static final String MODE_ASYNC = "ASYNC";

  /** 已经有结果的 trace 状态；重复消费时直接返回，不再建单。 */
  private static final Set<String> FINISHED_STATUSES = Set.of("SUCCESS", "FAILED", "EXPIRED");

  /** 锁定读取购买 trace，抢占受理权（CAS），建单成功后标记 trace 成功。 */
  private final OrderTraceRepository traces;

  /** 写订单（含扣库存、占券），并把订单号挂到 trace 上。 */
  private final OrderRepository orders;

  /** 把建单结果序列化后存进 trace，供前端轮询购买进度。 */
  private final OrderSnapshotService snapshots;

  /** 建单前的校验链（购买快照、当前报价等），任一失败即抛异常让事务回滚。 */
  private final OrderValidationChain validation;

  /** 在同一事务里写入"订单已创建"和"超时关单"两条 Outbox 消息，事务提交后才投递到 Broker。 */
  private final OrderMessageService messages;

  /** 发布商品缓存失效和库存预占整理事件（事务提交后处理）。 */
  private final ApplicationEventPublisher events;

  /** 记录重复提交/重复消费、建单成功数和受理到建单的耗时。 */
  private final OrderMetrics metrics;

  /** 读取订单支付有效期（秒），计算支付截止时间。 */
  private final OrderProcessingProperties properties;

  /** 建单前确认这笔购买仍持有有效的 Redis 库存预占。 */
  private final OrderInventoryService inventory;

  /**
   * 同步下单入口。事务提交后才算建单成功；Agent 确认购买时会加入调用方的外层事务。
   *
   * <p>trace 已经有结果（成功或失败）时直接返回，不会再次占用库存。
   */
  @Transactional
  public void create(long user, String id) {
    OrderTraceDO trace = traces.lock(id);
    if (trace.getUserId() != user) {
      throw new IllegalArgumentException("下单任务不存在或不属于当前用户");
    }
    if (!STATUS_INIT.equals(trace.getStatus())) {
      metrics.event("repeat_submit");
      return;
    }
    boolean confirmationExpired = !trace.getExpiresAt().isAfter(LocalDateTime.now());
    if (confirmationExpired) {
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    }
    if (!MODE_SYNC.equals(trace.getMode())) {
      throw new IllegalArgumentException("排队购买必须通过异步受理入口提交");
    }
    // 版本号 CAS 抢占受理权；并发的另一个请求已抢到时，本次直接返回。
    if (!traces.claim(trace)) {
      return;
    }
    // claim 成功会让版本号 +1，后续 succeed 的 CAS 必须基于新版本，所以重新读取。
    trace = traces.lock(id);
    build(trace);
  }

  /**
   * 异步消费入口：MQ 消费者对已受理的任务建单。
   *
   * <p>只有状态为 PROCESSING 的异步 trace 才能建单。还停在 INIT 的 trace 说明受理没有可靠提交，不能凭一条消息就建单； 已有结果的 trace
   * 说明是重复消息，直接返回。
   */
  @Transactional
  public void createQueued(String id) {
    OrderTraceDO trace = traces.lock(id);
    if (FINISHED_STATUSES.contains(trace.getStatus())) {
      metrics.event("repeat_consume");
      return;
    }
    boolean reliablyAccepted =
        MODE_ASYNC.equals(trace.getMode()) && STATUS_PROCESSING.equals(trace.getStatus());
    if (!reliablyAccepted) {
      throw new IllegalStateException("购买任务尚未可靠受理，不能由消息直接创建订单");
    }
    LocalDateTime processingDeadline = trace.getProcessingExpiresAt();
    boolean processingTimedOut =
        processingDeadline == null || !processingDeadline.isAfter(LocalDateTime.now());
    if (processingTimedOut) {
      throw new OrderRejectedException("QUEUE_TIMEOUT", "排队处理超时，本次未创建订单，请重新确认购买");
    }
    build(trace);
  }

  /** 同步和异步只是触发时机不同，库存、券、价格、购物车和订单的写入规则完全一样。 */
  private void build(OrderTraceDO trace) {
    long user = trace.getUserId();
    String traceId = trace.getTraceId();

    // 1. 复核报价，并确认这笔购买的 Redis 库存预占仍然有效。
    CheckoutQuoteVO quote = validation.validate(user, trace);
    inventory.requireReserved(trace, quote);

    // 2. 写订单（含扣库存、占券），并把订单号挂到 trace 上。
    String orderId = newOrderId();
    LocalDateTime paymentDeadline =
        LocalDateTime.now().plusSeconds(properties.getPaymentTtlSeconds());
    CouponVO coupon = quote.getCoupon();
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
        paymentDeadline);
    orders.attachTrace(orderId, traceId);

    // 3. 保存给前端轮询的结果快照，并用 CAS 把 trace 标记为成功。
    Map<String, Object> result = successResult(trace, quote, orderId, paymentDeadline);
    traces.succeed(trace, orderId, snapshots.write(result));
    metrics.committed("created", trace.getMode());
    metrics.completed(trace.getMode(), trace.getAcceptedAt());

    // 4. 同一事务里写入"订单已创建"和"超时关单"两条 Outbox 消息，事务提交后才真正投递到 Broker。
    messages.enqueue(OrderEventMessage.CREATED, orderId, user, quote.getPayAmount());
    messages.enqueuePaymentTimeout(orderId, user, paymentDeadline);

    // 5. 通知商品缓存失效，并触发 Redis 预占的确认整理。
    List<Long> skuIds = quote.getItems().stream().map(item -> item.getSkuId()).toList();
    events.publishEvent(new CatalogChanged(skuIds));
    events.publishEvent(new InventorySettlementEvent(traceId, null));
  }

  private static String newOrderId() {
    return "WW" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }

  private static Map<String, Object> successResult(
      OrderTraceDO trace, CheckoutQuoteVO quote, String orderId, LocalDateTime paymentDeadline) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("orderId", orderId);
    result.put("status", "PENDING_PAYMENT");
    result.put("payAmount", quote.getPayAmount());
    result.put("discountAmount", quote.getDiscountAmount());
    result.put("paymentExpiresAt", paymentDeadline);
    result.put("traceId", trace.getTraceId());
    result.put("submitMode", trace.getMode());
    result.put("message", "订单已创建，请在有效期内支付");
    return result;
  }
}
