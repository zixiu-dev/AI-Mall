package com.bitmall.agent.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.knowledge.dao.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {
  java.util.List<Long> lockSource();

  @org.apache.ibatis.annotations.Options(timeout = 2)
  java.util.List<java.util.Map<String, Object>> searchSql(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> listEnabled();
}
