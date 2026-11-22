package com.bitmall.agent.coupon.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.coupon.dao.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponEntity> {
  List<Map<String, Object>> listCouponsSql(
      Map<String, Object> values);

  List<Map<String, Object>> findUserCouponSql(
      Map<String, Object> values);

  List<Integer> countUserClaims(Map<String, Object> values);

  int incrementClaimedCount(Map<String, Object> values);

  int insertUserCoupon(Map<String, Object> values);

  List<Long> selectUserCouponId(Map<String, Object> values);
}
