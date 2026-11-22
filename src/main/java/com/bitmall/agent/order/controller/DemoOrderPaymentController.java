package com.bitmall.agent.order.controller;

import com.bitmall.agent.common.dto.UserRequest;
import com.bitmall.agent.order.service.OrderLifecycleService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 仅在演示开关开启时注册的支付按钮接口，真实支付接入须另做签名、金额及支付流水校验。 */
@RestController
@RequestMapping("/api/store/orders")
@ConditionalOnProperty(name = "app.order-processing.demo-payment-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoOrderPaymentController {
  /** 执行演示支付（不发起真实扣款）。 */
  private final OrderLifecycleService lifecycle;

  @PostMapping("/{orderId}/demo-pay")
  public Map<String, Object> pay(
      @PathVariable String orderId, @Valid @RequestBody UserRequest request) {
    return lifecycle.payDemo(request.resolvedUserId(), orderId);
  }
}
