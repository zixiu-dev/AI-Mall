package com.bitmall.agent.agent.service.tool.support;

import com.bitmall.agent.aftersales.model.SubmitAfterSalesCommand;
import com.bitmall.agent.order.model.CreateOrderCommand;

import java.math.BigDecimal;
import java.util.Map;

/** 把工具 JSON 中的数字和文本转换成业务服务需要的类型。 缺少必填字段时给出明确错误，避免业务代码到处读取不确定的 Map 值。 */
public final class ToolArguments {
    /** 本类只提供静态辅助方法，不允许外部创建无意义的实例。 */
    private ToolArguments() {}

    /** 把工具 Map 中的下单字段转成 CreateOrderCommand，金额使用 BigDecimal。 */
    public static CreateOrderCommand orderCommand(Map<String, Object> args) {
        return new CreateOrderCommand(
                text(args, "source", "DIRECT"),
                args.get("skuId") == null ? null : number(args, "skuId"),
                integer(args, "quantity", 1),
                args.get("addressId") == null ? null : number(args, "addressId"),
                args.get("userCouponId") == null ? null : number(args, "userCouponId"),
                new BigDecimal(requiredText(args, "expectedPayAmount")));
    }

    /** 把售后字段转成明确命令，缺少订单、类型、原因或确认金额时拒绝。 */
    public static SubmitAfterSalesCommand afterSalesCommand(Map<String, Object> args) {
        return new SubmitAfterSalesCommand(
                requiredText(args, "orderId"),
                requiredText(args, "type"),
                requiredText(args, "reason"),
                new BigDecimal(requiredText(args, "expectedRefundAmount")));
    }

    /** 读取必填文本，缺失或空白时返回明确参数错误。 */
    public static String requiredText(Map<String, Object> args, String key) {
        String value = text(args, key, null);
        if (value == null) throw new IllegalArgumentException("缺少参数 " + key);
        return value;
    }

    /** 读取可选文本，缺失或空白时使用调用者给的默认值。 */
    public static String text(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    /** 读取必填长整数编号，用于订单关联的商品、地址或券。 */
    public static long number(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) throw new IllegalArgumentException("缺少参数 " + key);
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }

    /** 读取数量等整数参数，没有传入时使用默认值。 */
    public static int integer(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        return value == null
                ? fallback
                : value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
    }
}
