package com.bitmall.agent.order.service;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.vo.OrderDetailVO;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;

/** 页面与 Agent 共用的订单业务入口，负责查询、稳定 trace 提交和确认摘要；建单及支付关单事务委托给专门服务。 */
@Service
@RequiredArgsConstructor
public class OrderService {
  /** 查询订单列表。 */
  private final OrderRepository mapper;

  /** 按下单参数试算结算报价（直接购买或购物车结算）。 */
  private final CheckoutService checkout;

  /** 取消订单。 */
  private final OrderLifecycleService lifecycle;

  /** 准备购买编号（trace），按来源键查找已有购买，查询购买进度。 */
  private final OrderTraceService traceService;

  /** 提交购买。 */
  private final OrderSubmissionService submissions;

  /** 按 traceId 或来源键直接查询 trace。 */
  private final OrderTraceRepository traces;

  /** 购买成功时，从 trace 保存的结果快照还原购买结果。 */
  private final OrderSnapshotService snapshots;

  /** 执行确认失败时，把对应购买记为失败。 */
  private final OrderTraceFailureService failures;

  /** 查询当前用户最近订单，并限制列表规模。 */
  public List<OrderDetailVO> orders(long userId, int limit) {
    return mapper.listOrders(userId, limit);
  }

  /** 查询指定用户的订单，未找到时明确说明不存在或不属于该用户。 */
  public OrderDetailVO order(long userId, String orderId) {
    return mapper
        .findOrder(userId, orderId)
        .orElseThrow(() -> new IllegalArgumentException("订单不存在或不属于当前用户"));
  }

  /** 普通页面和工具都必须提交已经准备的 trace，不再按请求参数临时生成新购买。 */
  public Map<String, Object> createOrder(long userId, String traceId) {
    return submissionResult(userId, submissions.submit(userId, traceId));
  }

  /** 在 Agent 提议阶段冻结快照，确认前不扣库存，也不发送建单命令。 */
  public void prepareAction(long userId, CreateOrderCommand command, String actionId) {
    traceService.prepare(userId, command, "agent:" + actionId);
  }

  /** 确认时复用提议阶段已生成的凭证，加入 ConfirmedActionExecutor 的外层事务。 */
  public Map<String, Object> submitAction(long userId, String actionId) {
    return submissionResult(
        userId, submissions.submit(userId, traceService.originTrace(userId, "agent:" + actionId)));
  }

  /** 出现不确定错误时按原 Agent 操作找回购买编号，不创建新购买。 */
  public OrderSubmitVO actionProgress(long userId, String actionId) {
    return traceService.get(userId, traceService.originTrace(userId, "agent:" + actionId));
  }

  /** 从确认操作恢复已绑定的购买进度；缺少归属一致的 trace 时省略该项。 */
  public Optional<OrderSubmitVO> findActionProgress(long userId, String actionId) {
    return traces.findOrigin("agent:" + actionId)
        .filter(trace -> trace.getUserId() == userId)
        .map(traceService::view);
  }

  /** Agent 事务回滚之后记录失败；终态成功不会被覆盖。 */
  public void failAction(long userId, String actionId, String reason) {
    Optional<OrderTraceDO> trace = traces.findOrigin("agent:" + actionId);
    if (trace.isPresent() && trace.get().getUserId() == userId) {
      failures.fail(trace.get().getTraceId(), "ACTION_FAILED", reason);
    }
  }

  /** 用户可通过订单查询工具查看自己的异步进度。 */
  public OrderSubmitVO progress(long userId, String traceId) {
    return traceService.get(userId, traceId);
  }

  /** 恢复旧接口和工具约定的业务回执，未知结果保留原 trace 查询，不伪造订单号。 */
  private Map<String, Object> submissionResult(long userId, OrderSubmitVO progress) {
    if ("SUCCESS".equals(progress.getStatus())) {
      return snapshots.result(traces.find(progress.getTraceId()).orElseThrow().getResultJson());
    }
    if (!"PROCESSING".equals(progress.getStatus())) {
      throw new IllegalStateException(progress.getMessage());
    }
    return Map.of(
        "success",
        true,
        "status",
        "PROCESSING",
        "traceId",
        progress.getTraceId(),
        "submitMode",
        progress.getMode(),
        "payAmount",
        progress.getPayAmount(),
        "message",
        progress.getMessage());
  }

  /** 取消待支付订单，状态判断和释放占用由独立事务服务统一完成。 */
  public Map<String, Object> cancelOrder(long userId, String orderId) {
    return lifecycle.cancel(userId, orderId);
  }

  /** 先通过订单查询核对归属，再返回订单状态和物流轨迹。 */
  public Map<String, Object> logistics(long userId, String orderId) {
    OrderDetailVO order = order(userId, orderId);
    return Map.of(
        "orderId", order.getId(), "orderStatus", order.getStatus(), "events", order.getLogistics());
  }

  /** 从真实试算生成下单确认摘要，不创建订单或扣库存。 */
  public String previewCreateOrder(long userId, CreateOrderCommand command) {
    CheckoutQuoteVO quote = quote(userId, command);
    String couponText = quote.getCoupon() == null ? "未使用优惠券" : "优惠券：" + quote.getCoupon().getName();
    return "创建订单：共 "
        + quote.getItems().size()
        + " 种商品，"
        + couponText
        + "，优惠 ¥"
        + quote.getDiscountAmount()
        + "，实付 ¥"
        + quote.getPayAmount()
        + "，收货地址："
        + quote.getAddress().display();
  }

  /** 查询订单并核对可取消状态，只返回取消影响说明。 */
  public String previewCancelOrder(long userId, String orderId) {
    OrderDetailVO order = order(userId, orderId);
    if (!"PENDING_PAYMENT".equals(order.getStatus())) {
      throw new IllegalStateException("当前订单状态不允许直接取消");
    }
    return "取消订单 "
        + order.getId()
        + "，订单金额 ¥"
        + order.getPayAmount()
        + "，取消后返还库存并解除优惠券占用；返还的券仍受有效期和使用条件限制。已支付订单需走售后流程";
  }

  /** 根据命令来源选择购物车或立即购买报价，统一供建单和预览使用。 */
  private CheckoutQuoteVO quote(long userId, CreateOrderCommand command) {
    return command.fromCart()
        ? checkout.cartCheckoutQuote(userId, command.getAddressId(), command.getUserCouponId())
        : checkout.checkoutQuote(
            userId,
            command.requiredSkuId(),
            command.getQuantity(),
            command.getAddressId(),
            command.getUserCouponId());
  }
}
