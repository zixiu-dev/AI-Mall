package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderMessageMapper extends BaseMapper<OrderMessageEntity> {
  int insertBuildSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findByIdSql(java.util.Map<String, Object> values);

  int requeueBuildSql(java.util.Map<String, Object> values);

  int cancelBuildSql(java.util.Map<String, Object> values);

  int insertSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findReadySql(java.util.Map<String, Object> values);

  int claimSql(java.util.Map<String, Object> values);

  int markSentSql(java.util.Map<String, Object> values);

  int scheduleRetrySql(java.util.Map<String, Object> values);
}
