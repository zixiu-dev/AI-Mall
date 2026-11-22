package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderMessageMapper extends BaseMapper<OrderMessageEntity> {
  int insertBuildSql(Map<String, Object> values);

  List<Map<String, Object>> findByIdSql(Map<String, Object> values);

  int matchesBuildSql(Map<String, Object> values);

  int requeueBuildSql(Map<String, Object> values);

  int cancelBuildSql(Map<String, Object> values);

  int insertSql(Map<String, Object> values);

  List<Map<String, Object>> findReadySql(Map<String, Object> values);

  int claimSql(Map<String, Object> values);

  int markSentSql(Map<String, Object> values);

  int scheduleRetrySql(Map<String, Object> values);
}
