package com.bitmall.agent.order.controller;

import com.bitmall.agent.checkout.dto.CheckoutRequest;
import com.bitmall.agent.common.dto.UserRequest;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dto.OrderSubmitRequest;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.order.service.OrderSubmissionService;
import com.bitmall.agent.order.service.OrderTraceService;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 页面订单 HTTP 入口。 订单查询、创建和取消都走 OrderService，和 Agent 确认执行共享同一套交易规则。 */
@RestController
@RequestMapping("/api/store")
public class OrderController {
  /** 准备和查询当前用户的购买凭证。 */
  private final OrderTraceService traces;

  /** 同步和异步下单共用受理入口。 */
  private final OrderSubmissionService submissions;

  /** 页面是否展示明确标注的演示支付按钮。 */
  private final boolean demoPaymentEnabled;

  private final OrderProcessingProperties processing;

  /** 依赖组件：订单交易业务服务。 */
  private final OrderService orders;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public OrderController(
      OrderService orders,
      OrderTraceService traces,
      OrderSubmissionService submissions,
      @Value("${app.order-processing.demo-payment-enabled:false}") boolean demoPaymentEnabled,
      OrderProcessingProperties processing) {
    this.processing = processing;
    this.orders = orders;
    this.traces = traces;
    this.submissions = submissions;
    this.demoPaymentEnabled = demoPaymentEnabled;
  }

  /** 只发号，不查询商品、不报价、不创建数据库购买记录。身份和内容在正式提交时绑定。 */
  @PostMapping("/order-tokens")
  public Map<String, String> issue() {
    return Map.of("traceId", java.util.UUID.randomUUID().toString());
  }

  /** 后端生成确认凭证；前端修改数量、地址或券后必须重新准备。 */
  @PostMapping("/order-traces")
  public OrderSubmitVO prepare(@jakarta.validation.Valid @RequestBody CheckoutRequest request) {
    return traces.prepare(request.resolvedUserId(), request.toCommand(), null);
  }

  /** 首次提交携带购买内容与发号编号；重试必须复用编号及原购买参数。 */
  @PostMapping("/orders/submit")
  public OrderSubmitVO submit(@jakarta.validation.Valid @RequestBody OrderSubmitRequest request) {
    return submissions.submit(
        request.resolvedUserId(), request.getTraceId(), request.purchaseCommand());
  }

  /** 只返回当前用户自己的处理进度。 */
  @GetMapping("/order-traces/{id}")
  public OrderSubmitVO progress(
      @PathVariable String id, @RequestParam(defaultValue = "10001") long userId) {
    return traces.get(userId, id);
  }

  /** 只公开页面需要的能力开关，不返回 Redis、MQ 等内部配置。 */
  @GetMapping("/order-options")
  public Map<String, Object> options() {
    return Map.of(
        "demoPaymentEnabled", demoPaymentEnabled, "submissionMode", processing.getSubmissionMode());
  }

  /** 查询当前用户最近的订单。 */
  @GetMapping("/orders")
  public Map<String, Object> orders(@RequestParam(defaultValue = "10001") long userId) {
    return Map.of("items", orders.orders(userId, 20));
  }

  /** 与 /orders/submit 相同的稳定凭证提交入口；旧的无 trace 建单方式已移除。 */
  @PostMapping("/orders")
  public OrderSubmitVO createOrder(
      @jakarta.validation.Valid @RequestBody OrderSubmitRequest request) {
    return submissions.submit(
        request.resolvedUserId(), request.getTraceId(), request.purchaseCommand());
  }

  /** 按订单编号申请取消，订单服务重新核对归属和状态。 */
  @PostMapping("/orders/{orderId}/cancel")
  public Map<String, Object> cancelOrder(
      @PathVariable String orderId, @RequestBody UserRequest request) {
    return orders.cancelOrder(request.resolvedUserId(), orderId);
  }
}
