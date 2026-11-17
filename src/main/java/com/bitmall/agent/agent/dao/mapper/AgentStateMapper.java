package com.bitmall.agent.agent.dao.mapper;

import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface AgentStateMapper {
  int putSql(java.util.Map<String, Object> values);

  int updateSql(java.util.Map<String, Object> values);

  java.util.List<String> payloadSql(java.util.Map<String, Object> values);

  java.util.List<Long> existsSql(java.util.Map<String, Object> values);

  java.util.List<String> listSessionIdsSql(java.util.Map<String, Object> values);

  int deleteSession(java.util.Map<String, Object> values);

  int deleteState(java.util.Map<String, Object> values);
}
