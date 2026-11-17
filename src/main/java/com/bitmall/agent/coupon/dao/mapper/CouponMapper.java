package com.bitmall.agent.coupon.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.coupon.dao.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponEntity> {
  java.util.List<java.util.Map<String, Object>> listCouponsSql(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findUserCouponSql(
      java.util.Map<String, Object> values);

  java.util.List<Integer> countUserClaims(java.util.Map<String, Object> values);

  int incrementClaimedCount(java.util.Map<String, Object> values);

  int insertUserCoupon(java.util.Map<String, Object> values);

  java.util.List<Long> selectUserCouponId(java.util.Map<String, Object> values);
}
