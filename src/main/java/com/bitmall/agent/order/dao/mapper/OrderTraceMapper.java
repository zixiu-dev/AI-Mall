package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderTraceEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderTraceMapper extends BaseMapper<OrderTraceEntity> {
  int insertSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> lockSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findOriginSql(java.util.Map<String, Object> values);

  int claimSql(java.util.Map<String, Object> values);

  int succeedSql(java.util.Map<String, Object> values);

  int failSql(java.util.Map<String, Object> values);

  java.util.List<String> recoveryIdsSql(java.util.Map<String, Object> values);

  int checkedSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> backlogSql(java.util.Map<String, Object> values);
}
