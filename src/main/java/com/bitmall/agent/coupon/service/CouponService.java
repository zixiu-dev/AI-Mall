package com.bitmall.agent.coupon.service;

import com.bitmall.agent.coupon.dao.repository.CouponRepository;
import com.bitmall.agent.coupon.vo.CouponVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 优惠券查询、预览和领取的业务服务。 预览只说明影响，领取才在事务中更新活动和用户券；Agent 和页面共用这套规则。 */
@Service
@RequiredArgsConstructor
public class CouponService {
  /** 查询用户优惠券；领券时条件扣减活动库存并插入用户券。 */
  private final CouponRepository mapper;

  /** 查询当前可展示活动和用户领取状态。 */
  public List<CouponVO> coupons(long userId) {
    return mapper.listCoupons(userId);
  }

  /** 先确认活动存在，再在事务中领取并返回生成的用户券编号。 */
  @Transactional
  public Map<String, Object> claimCoupon(long userId, long couponId) {
    CouponVO coupon = coupon(userId, couponId);
    long userCouponId = mapper.claimCoupon(userId, couponId);
    return Map.of(
        "success",
        true,
        "couponId",
        couponId,
        "userCouponId",
        userCouponId,
        "name",
        coupon.getName(),
        "message",
        "优惠券领取成功");
  }

  /** 核对未领取、活动时间及库存，只生成确认说明。 */
  public String previewClaimCoupon(long userId, long couponId) {
    CouponVO coupon = coupon(userId, couponId);
    if (coupon.claimed()) {
      throw new IllegalStateException("这张优惠券已经领取过了");
    }
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(coupon.getStartAt())) {
      throw new IllegalStateException("优惠券活动尚未开始");
    }
    if (!now.isBefore(coupon.getEndAt())) {
      throw new IllegalStateException("优惠券活动已经结束");
    }
    if (coupon.getRemainingStock() < 1) {
      throw new IllegalStateException("优惠券已经领完");
    }
    return "领取优惠券："
        + coupon.getName()
        + "，优惠 ¥"
        + coupon.getDiscountAmount()
        + "，使用门槛 ¥"
        + coupon.getThresholdAmount()
        + "，有效期至 "
        + coupon.getEndAt().toLocalDate();
  }

  /** 从当前用户可见活动中查找指定活动，不存在时明确拒绝。 */
  private CouponVO coupon(long userId, long couponId) {
    return mapper.listCoupons(userId).stream()
        .filter(value -> value.getId() == couponId)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("优惠券不存在或活动已经结束"));
  }
}
