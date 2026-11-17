package com.bitmall.agent.agent.service.tool.aftersales;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.aftersales.service.AfterSalesService;
import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;

import org.springframework.stereotype.Component;

import java.util.List;

/** 向 Agent 提供售后预检、申请提议和进度查询。 申请关联 after_sales 技能，并使用同一个业务服务核对资格和退款金额。 */
@Component
public class AfterSalesTools implements ToolProvider {
    /** 本功能声明的工具列表；保存的是函数定义，声明时不会直接执行交易。 */
    private final List<RegisteredTool> tools;

    /** 启动时声明本模块的工具名称、参数格式、执行函数和预览函数。这里只保存函数定义；模型实际调用或用户确认后才会执行函数。 */
    public AfterSalesTools(AfterSalesService afterSales) {
        tools =
                List.of(
                        tool(
                                "after_sales_precheck",
                                "检查订单是否能退款、退货或换货，并返回可退款金额。提交售后前必须调用。",
                                requiredSchema(
                                        new String[] {"orderId", "type"},
                                        "orderId",
                                        "string",
                                        "type",
                                        "string"),
                                "after_sales",
                                READ,
                                (ctx, a) ->
                                        afterSales.afterSalesPrecheck(
                                                ctx.getUserId(),
                                                text(a, "orderId", null),
                                                text(a, "type", null))),
                        writeTool(
                                "after_sales_submit",
                                "提交退款、退货或换货申请。",
                                requiredSchema(
                                        new String[] {
                                            "orderId", "type", "reason", "expectedRefundAmount"
                                        },
                                        "orderId",
                                        "string",
                                        "type",
                                        "string",
                                        "reason",
                                        "string",
                                        "expectedRefundAmount",
                                        "number"),
                                "after_sales",
                                (userId, a) ->
                                        afterSales.submitAfterSales(userId, afterSalesCommand(a)),
                                (userId, a) ->
                                        afterSales.previewAfterSales(userId, afterSalesCommand(a))),
                        tool(
                                "after_sales_query",
                                "按售后单号或订单号查询售后处理进度。",
                                schema("afterSalesId", "string", "orderId", "string"),
                                null,
                                READ,
                                (ctx, a) ->
                                        afterSales.afterSales(
                                                ctx.getUserId(),
                                                text(a, "afterSalesId", null),
                                                text(a, "orderId", null))));
    }

    /** 返回本功能已经声明的工具列表，由 ToolRegistry 统一注册。 */
    @Override
    public List<RegisteredTool> tools() {
        return tools;
    }
}
