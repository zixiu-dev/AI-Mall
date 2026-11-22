package com.bitmall.agent.checkout.controller;

import com.bitmall.agent.checkout.dto.CheckoutRequest;
import com.bitmall.agent.checkout.service.CheckoutService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 页面结算试算 HTTP 入口。 根据购买来源选择立即购买或购物车试算，只返回报价，不创建订单。 */
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class CheckoutController {
    /** 生成直接购买或购物车结算的报价。 */
    private final CheckoutService checkout;

    /** 按 source 选择立即购买或购物车试算，返回服务端算出的报价，不写订单。 */
    @PostMapping("/checkout/quote")
    public Map<String, Object> quote(@RequestBody CheckoutRequest request) {
        return "CART".equalsIgnoreCase(request.resolvedSource())
                ? checkout.cartCheckoutQuote(
                                request.resolvedUserId(),
                                request.getAddressId(),
                                request.getUserCouponId())
                        .toMap()
                : checkout.checkoutQuote(
                                request.resolvedUserId(),
                                request.requiredSkuId(),
                                request.resolvedQuantity(),
                                request.getAddressId(),
                                request.getUserCouponId())
                        .toMap();
    }
}
