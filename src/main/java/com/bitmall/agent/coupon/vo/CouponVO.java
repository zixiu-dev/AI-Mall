package com.bitmall.agent.coupon.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 优惠券活动及当前用户领取状态的组合视图。 id 是活动编号，userCouponId 是领取后生成的用户券编号；领取用前者，结算用后者。 */
@Getter
@EqualsAndHashCode
@ToString
public final class CouponVO {
    /** 优惠券活动编号，领取时使用；不是用户券编号。 */
    private final long id;

    private final String name;

    /** 优惠券要求达到的商品金额门槛。 */
    private final BigDecimal thresholdAmount;

    /** 这张优惠券的面额。 */
    private final BigDecimal discountAmount;

    /** 活动剩余可领取数量。 */
    private final int remainingStock;

    private final LocalDateTime startAt;

    private final LocalDateTime endAt;

    /** 用户领取后生成的券编号，结算时使用它。 */
    private final Long userCouponId;

    /** 这张用户券的状态，例如 AVAILABLE 或 USED。 */
    private final String userStatus;

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

    /** 根据是否存在用户券编号判断当前用户是否已经领取。 */
    public boolean claimed() {
        return userCouponId != null;
    }
}
