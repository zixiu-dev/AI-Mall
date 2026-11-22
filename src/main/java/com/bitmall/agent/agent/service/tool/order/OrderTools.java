package com.bitmall.agent.agent.service.tool.order;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.order.service.OrderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 向 Agent 提供订单查询、下单提议、取消提议和物流查询。 模型参数先转换为普通业务对象，写操作由确认流程控制。 */
@Component
@RequiredArgsConstructor
public class OrderTools implements ToolProvider {
  /** 订单相关工具的业务入口：查询订单/物流/购买进度，预览下单和取消，执行取消。 */
  private final OrderService orders;

  /** 声明本功能的工具定义，启动时由 ToolRegistry 收集并检查名称。 */
  @Override
  public List<RegisteredTool> tools() {
    return List.of(
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
}
