package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import java.util.List;
import java.util.Map;

/** 知识索引存储契约；交易与 Agent 不依赖具体向量数据库。 */
public interface KnowledgeIndexStore {
  void begin(String version, String model, String modelVersion, int dimension, int documents);

  void fail(String version);

  void publish(String version, List<KnowledgeChunk> chunks, Runnable verifySource);

  KnowledgeIndexSnapshot published(int maximum);

  List<Map<String, Object>> search(
      String version, String query, String brand, String type, int limit);

  /** 仅原生向量后端实现；旧兼容后端由检索器显式走 Java 精确扫描。 */
  default List<Map<String, Object>> vectorSearch(
      String version, double[] vector, String brand, String type, int limit) {
    throw new UnsupportedOperationException("当前兼容后端没有数据库向量查询");
  }
}
