package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.PendingActionEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface PendingActionMapper extends BaseMapper<PendingActionEntity> {
  int insertSql(Map<String, Object> values);

  List<Map<String, Object>> findSql(Map<String, Object> values);

  List<Map<String, Object>> latestPendingSql(
      Map<String, Object> values);

  int decideSql(Map<String, Object> values);

  int markExecutedSql(Map<String, Object> values);

  int markFailedSql(Map<String, Object> values);
}
