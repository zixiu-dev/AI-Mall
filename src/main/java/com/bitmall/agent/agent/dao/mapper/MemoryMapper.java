package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.MemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface MemoryMapper extends BaseMapper<MemoryEntity> {
  List<Map<String, Object>> findByUserSql(Map<String, Object> values);

  int updatePreference(Map<String, Object> values);

  int insertPreference(Map<String, Object> values);
}
