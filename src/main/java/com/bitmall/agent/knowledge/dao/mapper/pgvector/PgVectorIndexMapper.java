package com.bitmall.agent.knowledge.dao.mapper.pgvector;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** 只由独立 PG SqlSessionFactory 注册；故意不加全局 @Mapper，避免误用商城数据源。 */
public interface PgVectorIndexMapper {
  int begin(Map<String, Object> row);

  int fail(String version);

  Integer lockHead();

  int insertChunk(Map<String, Object> row);

  int complete(@Param("version") String version, @Param("count") int count);

  int switchHead(String version);

  Map<String, Object> published();

  List<Map<String, Object>> chunks(@Param("version") String version, @Param("limit") int limit);

  List<Map<String, Object>> keyword(Map<String, Object> args);

  List<Map<String, Object>> exact(Map<String, Object> args);

  List<Map<String, Object>> ann(Map<String, Object> args);

  int scopeCount(Map<String, Object> args);

  void setLockTimeout();

  void setStatementTimeout();

  void setIterativeScan();

  void setEfSearch();
}
