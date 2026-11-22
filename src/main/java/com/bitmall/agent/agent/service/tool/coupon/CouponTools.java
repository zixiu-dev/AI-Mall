package com.bitmall.agent.agent.service.tool.coupon;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.coupon.service.CouponService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供优惠券查询和领取提议。 领取工具关联 coupon_ops 技能，并分别绑定真实领取函数与确认前预览函数。 */
@Component
@RequiredArgsConstructor
public class CouponTools implements ToolProvider {
    /** 查询用户优惠券，预览和执行领券。 */
    private final CouponService coupons;

    /** 声明本功能的工具定义，启动时由 ToolRegistry 收集并检查名称。 */
    @Override
    public List<RegisteredTool> tools() {
        return List.of(
                tool(
                        "coupon_list",
                        "查询当前可领取优惠券、用户已领取优惠券、使用门槛、优惠金额和剩余库存。",
                        NO_ARGS,
                        null,
                        READ,
                        (ctx, a) -> coupons.coupons(ctx.getUserId())),
                writeTool(
                        "coupon_claim",
                        "为用户明确选定的优惠券生成待确认卡，不会立即领取。必须先使用 coupon_list 核对活动、库存和用户领取状态；couponId"
                                + " 是活动 ID，不是结算使用的 userCouponId。",
                        requiredSchema("couponId", "integer"),
                        "coupon_ops",
                        (userId, a) -> coupons.claimCoupon(userId, number(a, "couponId")),
                        (userId, a) -> coupons.previewClaimCoupon(userId, number(a, "couponId"))));
    }
}
