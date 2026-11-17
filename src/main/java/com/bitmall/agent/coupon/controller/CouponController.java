package com.bitmall.agent.coupon.controller;

import com.bitmall.agent.common.dto.UserRequest;
import com.bitmall.agent.coupon.service.CouponService;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 手动优惠券 HTTP 入口。 查询和领取均调用 CouponService；活动编号与用户领取后的券编号是两种不同编号。 */
@RestController
@RequestMapping("/api/store")
public class CouponController {
    /** 依赖组件：优惠券查询、预览和领取的业务服务。 */
    private final CouponService coupons;

    /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
    public CouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    /** 查询优惠券活动，并附带当前用户是否领取、是否已使用的信息。 */
    @GetMapping("/coupons")
    public Map<String, Object> coupons(@RequestParam(defaultValue = "10001") long userId) {
        return Map.of("items", coupons.coupons(userId));
    }

    /** 按优惠券活动编号领取，服务层在事务中检查活动和库存。 */
    @PostMapping("/coupons/{couponId}/claim")
    public Map<String, Object> claimCoupon(
            @PathVariable long couponId, @RequestBody UserRequest request) {
        return coupons.claimCoupon(request.resolvedUserId(), couponId);
    }
}
