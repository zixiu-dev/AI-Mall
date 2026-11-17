package com.bitmall.agent.knowledge.service.retrieval;

import java.util.List;
import java.util.Map;

/** 批量查询表达增强，不再次拆业务任务、不持有其他工具权限；返回 queryId 对应的候选表达。 */
public interface QueryEnhancer {
  Map<String, List<String>> enhance(
      Map<String, String> queries, List<String> history, long deadlineNanos);
}
