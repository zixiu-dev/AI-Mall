package com.bitmall.agent.agent.service.tool.checkout;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.checkout.service.CheckoutService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供结算试算工具。 把模型给出的规格、数量、地址和用户券编号交给 CheckoutService，返回服务端核算的报价。 */
@Component
@RequiredArgsConstructor
public class CheckoutTools implements ToolProvider {
    /** 生成结算报价（商品、优惠、运费、应付金额）。 */
    private final CheckoutService checkout;

    /** 声明本功能的工具定义，启动时由 ToolRegistry 收集并检查名称。 */
    @Override
    public List<RegisteredTool> tools() {
        return List.of(
                tool(
                        "checkout_quote",
                        "已知 SKU 时的结算试算工具：只需 skuId 和 quantity，返回商品名称、规格、库存、默认地址 addressId"
                                + " 和实付 payAmount。addressId 省略使用默认地址；userCouponId"
                                + " 省略表示不用券。仅试算，不创建订单。",
                        requiredSchema(
                                new String[] {"skuId", "quantity"},
                                "skuId",
                                "integer",
                                "quantity",
                                "integer",
                                "addressId",
                                "integer",
                                "userCouponId",
                                "integer"),
                        null,
                        READ,
                        (ctx, a) ->
                                checkout.checkoutQuote(
                                                ctx.getUserId(),
                                                number(a, "skuId"),
                                                integer(a, "quantity", 1),
                                                a.get("addressId") == null
                                                        ? null
                                                        : number(a, "addressId"),
                                                a.get("userCouponId") == null
                                                        ? null
                                                        : number(a, "userCouponId"))
                                        .toMap()));
    }
}
