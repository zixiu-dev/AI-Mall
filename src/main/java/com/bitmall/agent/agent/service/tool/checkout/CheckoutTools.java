package com.bitmall.agent.agent.service.tool.checkout;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.checkout.service.CheckoutService;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供结算试算工具。 把模型给出的规格、数量、地址和用户券编号交给 CheckoutService，返回服务端核算的报价。 */
@Component
public class CheckoutTools implements ToolProvider {
    /** 本功能声明的工具列表；保存的是函数定义，声明时不会直接执行交易。 */
    private final List<RegisteredTool> tools;

    /** 启动时声明本模块的工具名称、参数格式、执行函数和预览函数。这里只保存函数定义；模型实际调用或用户确认后才会执行函数。 */
    public CheckoutTools(CheckoutService checkout) {
        tools =
                List.of(
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

    /** 返回本功能已经声明的工具列表，由 ToolRegistry 统一注册。 */
    @Override
    public List<RegisteredTool> tools() {
        return tools;
    }
}
