package com.bitmall.agent.agent.service.tool.order;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.order.service.OrderService;
import java.util.List;
import org.springframework.stereotype.Component;

/** 向 Agent 提供订单查询、下单提议、取消提议和物流查询。 模型参数先转换为普通业务对象，写操作由确认流程控制。 */
@Component
public class OrderTools implements ToolProvider {
  /** 本功能声明的工具列表；保存的是函数定义，声明时不会直接执行交易。 */
  private final List<RegisteredTool> tools;

  /** 启动时声明本模块的工具名称、参数格式、执行函数和预览函数。这里只保存函数定义；模型实际调用或用户确认后才会执行函数。 */
  public OrderTools(OrderService orders) {
    tools =
        List.of(
            tool(
                "order_query",
                "查询当前用户的订单列表或指定订单详情。",
                schema("orderId", "string", "traceId", "string", "limit", "integer"),
                null,
                READ,
                (ctx, a) ->
                    a.get("traceId") != null
                        ? orders.progress(ctx.getUserId(), requiredText(a, "traceId"))
                        : a.get("orderId") == null
                            ? orders.orders(ctx.getUserId(), integer(a, "limit", 5))
                            : orders.order(ctx.getUserId(), text(a, "orderId", null))),
            writeTool(
                "order_create",
                "生成待确认订单卡。先调用 checkout_quote，把回包中的 addressId 和 payAmount 分别传入"
                    + " addressId 和 expectedPayAmount；实际建单只在用户点击确认后执行。",
                requiredSchema(
                    new String[] {"skuId", "quantity", "addressId", "expectedPayAmount"},
                    "skuId",
                    "integer",
                    "quantity",
                    "integer",
                    "addressId",
                    "integer",
                    "userCouponId",
                    "integer",
                    "expectedPayAmount",
                    "number"),
                "order_create_flow",
                (userId, a) -> {
                  throw new IllegalStateException("下单必须通过绑定 trace 的确认操作执行");
                },
                (userId, a) -> orders.previewCreateOrder(userId, orderCommand(a))),
            writeTool(
                "order_cancel",
                "生成取消订单确认卡，不直接取消。先用 order_query 确认目标订单；仅 PENDING_PAYMENT" + " 可取消，确认后后端重新校验并执行。",
                requiredSchema("orderId", "string"),
                "order_cancel_flow",
                (userId, a) -> orders.cancelOrder(userId, requiredText(a, "orderId")),
                (userId, a) -> orders.previewCancelOrder(userId, requiredText(a, "orderId"))),
            tool(
                "logistics_query",
                "查询当前用户指定订单的物流轨迹和配送状态。",
                requiredSchema("orderId", "string"),
                null,
                READ,
                (ctx, a) -> orders.logistics(ctx.getUserId(), text(a, "orderId", null))));
  }

  /** 返回本功能已经声明的工具列表，由 ToolRegistry 统一注册。 */
  @Override
  public List<RegisteredTool> tools() {
    return tools;
  }
}
