package com.bitmall.agent.agent.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface AgentStateMapper {
  int putSql(Map<String, Object> values);

  int updateSql(Map<String, Object> values);

  List<String> payloadSql(Map<String, Object> values);

  List<Long> existsSql(Map<String, Object> values);

  List<String> listSessionIdsSql(Map<String, Object> values);

  int deleteSession(Map<String, Object> values);

  int deleteState(Map<String, Object> values);
}
