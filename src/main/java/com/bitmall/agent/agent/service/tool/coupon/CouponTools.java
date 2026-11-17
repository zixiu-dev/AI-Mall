package com.bitmall.agent.agent.service.tool.coupon;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.coupon.service.CouponService;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供优惠券查询和领取提议。 领取工具关联 coupon_ops 技能，并分别绑定真实领取函数与确认前预览函数。 */
@Component
public class CouponTools implements ToolProvider {
    /** 本功能声明的工具列表；保存的是函数定义，声明时不会直接执行交易。 */
    private final List<RegisteredTool> tools;

    /** 启动时声明本模块的工具名称、参数格式、执行函数和预览函数。这里只保存函数定义；模型实际调用或用户确认后才会执行函数。 */
    public CouponTools(CouponService coupons) {
        tools =
                List.of(
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
                                (userId, a) ->
                                        coupons.previewClaimCoupon(userId, number(a, "couponId"))));
    }

    /** 返回本功能已经声明的工具列表，由 ToolRegistry 统一注册。 */
    @Override
    public List<RegisteredTool> tools() {
        return tools;
    }
}
