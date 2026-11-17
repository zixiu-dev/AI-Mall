package com.bitmall.agent.aftersales.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** 提交售后时交给业务服务的参数。 构造时检查订单、类型和原因是否齐全；业务服务会再次检查资格和退款金额，不能直接相信模型提供的金额。 */
public final class SubmitAfterSalesCommand {
    /** 关联订单编号。 */
    private final String orderId;

    /** 售后类型：退款 REFUND、退货 RETURN 或换货 EXCHANGE。 */
    private final String type;

    /** 用户提交的售后原因。 */
    private final String reason;

    /** 用户确认时看到的退款金额，执行前需要重新核对。 */
    private final BigDecimal expectedRefundAmount;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public SubmitAfterSalesCommand(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("type") String type,
            @JsonProperty("reason") String reason,
            @JsonProperty("expectedRefundAmount") BigDecimal expectedRefundAmount) {
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("缺少参数 orderId");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("缺少参数 type");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("缺少参数 reason");
        type = type.toUpperCase(Locale.ROOT);
        this.orderId = orderId;
        this.type = type;
        this.reason = reason;
        this.expectedRefundAmount = expectedRefundAmount;
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

    /** 获取用户确认时看到的退款金额，执行前需要重新核对。 */
    public BigDecimal getExpectedRefundAmount() {
        return expectedRefundAmount;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        SubmitAfterSalesCommand that = (SubmitAfterSalesCommand) other;
        return Objects.equals(orderId, that.orderId)
                && Objects.equals(type, that.type)
                && Objects.equals(reason, that.reason)
                && Objects.equals(expectedRefundAmount, that.expectedRefundAmount);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(orderId, type, reason, expectedRefundAmount);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "SubmitAfterSalesCommand["
                + "orderId="
                + orderId
                + ", type="
                + type
                + ", reason="
                + reason
                + ", expectedRefundAmount="
                + expectedRefundAmount
                + "]";
    }
}
