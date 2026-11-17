package com.bitmall.agent.knowledge.dao.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 索引元数据、证据块与发布指针的 SQL 映射；不在 Mapper 内调用模型。 */
@Mapper
public interface KnowledgeIndexMapper {
  @org.apache.ibatis.annotations.Options(timeout = 2)
  List<Map<String, Object>> searchChunks(Map<String, Object> values);

  int begin(Map<String, Object> values);

  int fail(String version);

  Integer lockHead();

  List<Long> lockDocuments();

  int insertChunk(Map<String, Object> values);

  int complete(@Param("version") String version, @Param("count") int count);

  int switchHead(String version);

  Map<String, Object> published();

  List<Map<String, Object>> chunks(@Param("version") String version, @Param("limit") int limit);
}
