package com.bitmall.agent.aftersales.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 售后申请的查询结果。 记录关联订单、售后类型、申请原因、退款金额和当前处理状态。 */
@Getter
@EqualsAndHashCode
@ToString
public final class AfterSalesVO {
    private final String id;

    private final String orderId;

    /** 售后类型：退款 REFUND、退货 RETURN 或换货 EXCHANGE。 */
    private final String type;

    private final String reason;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 经服务端核算的退款金额。 */
    private final BigDecimal refundAmount;

    private final LocalDateTime createdAt;

    @JsonCreator
    public AfterSalesVO(
            @JsonProperty("id") String id,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("type") String type,
            @JsonProperty("reason") String reason,
            @JsonProperty("status") String status,
            @JsonProperty("refundAmount") BigDecimal refundAmount,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.type = type;
        this.reason = reason;
        this.status = status;
        this.refundAmount = refundAmount;
        this.createdAt = createdAt;
    }
}
