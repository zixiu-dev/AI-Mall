package com.bitmall.agent.aftersales.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 提交售后时交给业务服务的参数。 构造时检查订单、类型和原因是否齐全；业务服务会再次检查资格和退款金额，不能直接相信模型提供的金额。 */
@Getter
@EqualsAndHashCode
@ToString
public final class SubmitAfterSalesCommand {
    private final String orderId;

    /** 售后类型：退款 REFUND、退货 RETURN 或换货 EXCHANGE。 */
    private final String type;

    private final String reason;

    /** 用户确认时看到的退款金额，执行前需要重新核对。 */
    private final BigDecimal expectedRefundAmount;

    @JsonCreator
    public SubmitAfterSalesCommand(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("type") String type,
            @JsonProperty("reason") String reason,
            @JsonProperty("expectedRefundAmount") BigDecimal expectedRefundAmount) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("缺少参数 orderId");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("缺少参数 type");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("缺少参数 reason");
        }
        type = type.toUpperCase(Locale.ROOT);
        this.orderId = orderId;
        this.type = type;
        this.reason = reason;
        this.expectedRefundAmount = expectedRefundAmount;
    }
}
