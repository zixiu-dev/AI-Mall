package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.PendingActionEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface PendingActionMapper extends BaseMapper<PendingActionEntity> {
  int insertSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> latestPendingSql(
      java.util.Map<String, Object> values);

  int decideSql(java.util.Map<String, Object> values);

  int markExecutedSql(java.util.Map<String, Object> values);

  int markFailedSql(java.util.Map<String, Object> values);
}
