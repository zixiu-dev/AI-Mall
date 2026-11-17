package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.MemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface MemoryMapper extends BaseMapper<MemoryEntity> {
  java.util.List<java.util.Map<String, Object>> findByUserSql(java.util.Map<String, Object> values);

  int updatePreference(java.util.Map<String, Object> values);

  int insertPreference(java.util.Map<String, Object> values);
}
