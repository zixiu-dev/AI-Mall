package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.SkillEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {
  java.util.List<java.util.Map<String, Object>> listEnabledSql(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findSql(java.util.Map<String, Object> values);

  java.util.List<String> loadedSkillCodesSql(java.util.Map<String, Object> values);

  int markLoadedSql(java.util.Map<String, Object> values);

  int replaceLoadedSql(java.util.Map<String, Object> values);
}
