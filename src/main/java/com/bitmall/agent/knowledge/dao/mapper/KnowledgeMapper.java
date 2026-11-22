package com.bitmall.agent.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.knowledge.dao.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Options;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {
  List<Long> lockSource();

  @Options(timeout = 2)
  List<Map<String, Object>> searchSql(Map<String, Object> values);

  List<Map<String, Object>> listEnabled();
}
