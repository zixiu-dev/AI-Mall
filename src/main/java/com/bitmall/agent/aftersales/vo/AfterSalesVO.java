package com.bitmall.agent.aftersales.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 售后申请的查询结果。 记录关联订单、售后类型、申请原因、退款金额和当前处理状态。 */
public final class AfterSalesVO {
    /** 售后申请编号。 */
    private final String id;

    /** 关联订单编号。 */
    private final String orderId;

    /** 售后类型：退款 REFUND、退货 RETURN 或换货 EXCHANGE。 */
    private final String type;

    /** 用户提交的售后原因。 */
    private final String reason;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 经服务端核算的退款金额。 */
    private final BigDecimal refundAmount;

    /** 数据创建时间。 */
    private final LocalDateTime createdAt;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取售后申请编号。 */
    public String getId() {
        return id;
    }

    /** 获取关联订单编号。 */
    public String getOrderId() {
        return orderId;
    }

    /** 获取售后类型：退款 REFUND、退货 RETURN 或换货 EXCHANGE。 */
    public String getType() {
        return type;
    }

    /** 获取用户提交的售后原因。 */
    public String getReason() {
        return reason;
    }

    /** 获取当前业务状态，由服务端维护。 */
    public String getStatus() {
        return status;
    }

    /** 获取经服务端核算的退款金额。 */
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    /** 获取数据创建时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AfterSalesVO that = (AfterSalesVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(orderId, that.orderId)
                && Objects.equals(type, that.type)
                && Objects.equals(reason, that.reason)
                && Objects.equals(status, that.status)
                && Objects.equals(refundAmount, that.refundAmount)
                && Objects.equals(createdAt, that.createdAt);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(id, orderId, type, reason, status, refundAmount, createdAt);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "AfterSalesVO["
                + "id="
                + id
                + ", orderId="
                + orderId
                + ", type="
                + type
                + ", reason="
                + reason
                + ", status="
                + status
                + ", refundAmount="
                + refundAmount
                + ", createdAt="
                + createdAt
                + "]";
    }
}
