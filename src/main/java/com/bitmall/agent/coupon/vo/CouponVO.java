package com.bitmall.agent.coupon.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 优惠券活动及当前用户领取状态的组合视图。 id 是活动编号，userCouponId 是领取后生成的用户券编号；领取用前者，结算用后者。 */
public final class CouponVO {
    /** 优惠券活动编号，领取时使用；不是用户券编号。 */
    private final long id;

    /** 优惠券活动名称。 */
    private final String name;

    /** 优惠券要求达到的商品金额门槛。 */
    private final BigDecimal thresholdAmount;

    /** 这张优惠券的面额。 */
    private final BigDecimal discountAmount;

    /** 活动剩余可领取数量。 */
    private final int remainingStock;

    /** 活动或优惠券开始生效的时间。 */
    private final LocalDateTime startAt;

    /** 活动或优惠券失效的时间。 */
    private final LocalDateTime endAt;

    /** 用户领取后生成的券编号，结算时使用它。 */
    private final Long userCouponId;

    /** 这张用户券的状态，例如 AVAILABLE 或 USED。 */
    private final String userStatus;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public CouponVO(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("thresholdAmount") BigDecimal thresholdAmount,
            @JsonProperty("discountAmount") BigDecimal discountAmount,
            @JsonProperty("remainingStock") int remainingStock,
            @JsonProperty("startAt") LocalDateTime startAt,
            @JsonProperty("endAt") LocalDateTime endAt,
            @JsonProperty("userCouponId") Long userCouponId,
            @JsonProperty("userStatus") String userStatus) {
        this.id = id;
        this.name = name;
        this.thresholdAmount = thresholdAmount;
        this.discountAmount = discountAmount;
        this.remainingStock = remainingStock;
        this.startAt = startAt;
        this.endAt = endAt;
        this.userCouponId = userCouponId;
        this.userStatus = userStatus;
    }

    /** 获取优惠券活动编号，领取时使用；不是用户券编号。 */
    public long getId() {
        return id;
    }

    /** 获取优惠券活动名称。 */
    public String getName() {
        return name;
    }

    /** 获取优惠券要求达到的商品金额门槛。 */
    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    /** 获取这张优惠券的面额。 */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    /** 获取活动剩余可领取数量。 */
    public int getRemainingStock() {
        return remainingStock;
    }

    /** 获取活动或优惠券开始生效的时间。 */
    public LocalDateTime getStartAt() {
        return startAt;
    }

    /** 获取活动或优惠券失效的时间。 */
    public LocalDateTime getEndAt() {
        return endAt;
    }

    /** 获取用户领取后生成的券编号，结算时使用它。 */
    public Long getUserCouponId() {
        return userCouponId;
    }

    /** 获取这张用户券的状态，例如 AVAILABLE 或 USED。 */
    public String getUserStatus() {
        return userStatus;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CouponVO that = (CouponVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(thresholdAmount, that.thresholdAmount)
                && Objects.equals(discountAmount, that.discountAmount)
                && Objects.equals(remainingStock, that.remainingStock)
                && Objects.equals(startAt, that.startAt)
                && Objects.equals(endAt, that.endAt)
                && Objects.equals(userCouponId, that.userCouponId)
                && Objects.equals(userStatus, that.userStatus);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                thresholdAmount,
                discountAmount,
                remainingStock,
                startAt,
                endAt,
                userCouponId,
                userStatus);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CouponVO["
                + "id="
                + id
                + ", name="
                + name
                + ", thresholdAmount="
                + thresholdAmount
                + ", discountAmount="
                + discountAmount
                + ", remainingStock="
                + remainingStock
                + ", startAt="
                + startAt
                + ", endAt="
                + endAt
                + ", userCouponId="
                + userCouponId
                + ", userStatus="
                + userStatus
                + "]";
    }

    /** 根据是否存在用户券编号判断当前用户是否已经领取。 */
    public boolean claimed() {
        return userCouponId != null;
    }
}
