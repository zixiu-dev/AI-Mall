package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderTraceEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderTraceMapper extends BaseMapper<OrderTraceEntity> {
  int insertSql(Map<String, Object> values);

  List<Map<String, Object>> findSql(Map<String, Object> values);

  List<Map<String, Object>> lockSql(Map<String, Object> values);

  List<Map<String, Object>> findOriginSql(Map<String, Object> values);

  int claimSql(Map<String, Object> values);

  int succeedSql(Map<String, Object> values);

  int failSql(Map<String, Object> values);

  List<String> recoveryIdsSql(Map<String, Object> values);

  int checkedSql(Map<String, Object> values);

  List<Map<String, Object>> backlogSql(Map<String, Object> values);

  List<Map<String, Object>> asyncBuildBacklogSql();
}
