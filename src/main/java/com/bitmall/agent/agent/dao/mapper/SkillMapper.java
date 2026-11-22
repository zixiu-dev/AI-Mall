package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.SkillEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {
  List<Map<String, Object>> listEnabledSql(
      Map<String, Object> values);

  List<Map<String, Object>> findSql(Map<String, Object> values);

  List<String> loadedSkillCodesSql(Map<String, Object> values);

  int markLoadedSql(Map<String, Object> values);

  int replaceLoadedSql(Map<String, Object> values);
}
