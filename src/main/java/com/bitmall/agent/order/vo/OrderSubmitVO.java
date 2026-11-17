package com.bitmall.agent.order.vo;

import java.math.BigDecimal;

/** 页面与 Agent 共用的下单进度。只返回用户可见状态，不暴露内部快照或其他用户信息。 */
public final class OrderSubmitVO {
    /** 继续提交、重试和查询时复用的编号。 */
    private final String traceId;

    /** 下单任务状态，处理中不等于订单成功。 */
    private final String status;

    /** SYNC 或 ASYNC。 */
    private final String mode;

    /** 成功后才有的订单编号。 */
    private final String orderId;

    /** 用户确认的应付金额。 */
    private final BigDecimal payAmount;

    /** 失败时的稳定分类。 */
    private final String failureCode;

    /** 可以直接展示的状态说明。 */
    private final String message;

    /** 显式保存全部字段，读取时使用下方 Getter。 */
    public OrderSubmitVO(
            String traceId,
            String status,
            String mode,
            String orderId,
            BigDecimal payAmount,
            String failureCode,
            String message) {
        this.traceId = traceId;
        this.status = status;
        this.mode = mode;
        this.orderId = orderId;
        this.payAmount = payAmount;
        this.failureCode = failureCode;
        this.message = message;
    }

    /** 获取继续提交、重试和查询时复用的编号。 */
    public String getTraceId() {
        return traceId;
    }

    /** 获取下单任务状态，处理中不等于订单成功。 */
    public String getStatus() {
        return status;
    }

    /** 获取SYNC 或 ASYNC。 */
    public String getMode() {
        return mode;
    }

    /** 获取成功后才有的订单编号。 */
    public String getOrderId() {
        return orderId;
    }

    /** 获取用户确认的应付金额。 */
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    /** 获取失败时的稳定分类。 */
    public String getFailureCode() {
        return failureCode;
    }

    /** 获取可以直接展示的状态说明。 */
    public String getMessage() {
        return message;
    }
}
