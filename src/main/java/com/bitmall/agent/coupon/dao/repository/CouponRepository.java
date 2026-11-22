package com.bitmall.agent.coupon.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.coupon.dao.mapper.CouponMapper;
import com.bitmall.agent.coupon.vo.CouponVO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 优惠券活动和用户券的数据访问组件。 查询 coupon、user_coupon，并用带条件的更新避免超领；领取与结算事务由 Service 管理。 */
@Repository
@RequiredArgsConstructor
public class CouponRepository {
  /** 优惠券及用户券的 SQL，定义在 mapper/coupon/CouponMapper.xml。 */
  private final CouponMapper mapper;

  /** 查询有效优惠券活动，并左连接当前用户的领取记录。 */
  public List<CouponVO> listCoupons(long userId) {
    return mapper.listCouponsSql(parameters("userId", userId)).stream()
        .map(
            row ->
                new CouponVO(
                    RowValues.longValue(row, "id"),
                    RowValues.string(row, "name"),
                    RowValues.decimal(row, "threshold_amount"),
                    RowValues.decimal(row, "discount_amount"),
                    RowValues.intValue(row, "remaining_stock"),
                    RowValues.timestamp(row, "start_at").toLocalDateTime(),
                    RowValues.timestamp(row, "end_at").toLocalDateTime(),
                    RowValues.value(row, "user_coupon_id") == null
                        ? null
                        : RowValues.longValue(row, "user_coupon_id"),
                    RowValues.string(row, "user_status")))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** 按用户券编号和用户读取券，结算服务据此检查门槛及使用状态。 */
  public Optional<CouponVO> findUserCoupon(long userId, long userCouponId) {
    return RowValues.optional(
        mapper
            .findUserCouponSql(parameters("userId", userId, "userCouponId", userCouponId))
            .stream()
            .map(
                row ->
                    new CouponVO(
                        RowValues.longValue(row, "id"),
                        RowValues.string(row, "name"),
                        RowValues.decimal(row, "threshold_amount"),
                        RowValues.decimal(row, "discount_amount"),
                        RowValues.intValue(row, "remaining_stock"),
                        RowValues.timestamp(row, "start_at").toLocalDateTime(),
                        RowValues.timestamp(row, "end_at").toLocalDateTime(),
                        RowValues.longValue(row, "user_coupon_id"),
                        RowValues.string(row, "user_status")))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 条件扣减活动可领取库存后插入用户券；服务层事务保证两个写操作一起完成。 */
  public long claimCoupon(long userId, long couponId) {
    if (RowValues.required(
            mapper.countUserClaims(parameters("userId", userId, "couponId", couponId)))
        > 0) {
      throw new IllegalStateException("这张优惠券已经领取过了");
    }
    int stock = mapper.incrementClaimedCount(parameters("couponId", couponId));
    if (stock != 1) {
      throw new IllegalStateException("优惠券已领完或活动已结束");
    }
    mapper.insertUserCoupon(parameters("userId", userId, "couponId", couponId));
    return RowValues.required(
        mapper.selectUserCouponId(parameters("userId", userId, "couponId", couponId)));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
