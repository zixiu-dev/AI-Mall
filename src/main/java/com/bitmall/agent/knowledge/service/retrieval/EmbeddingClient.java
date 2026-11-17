package com.bitmall.agent.knowledge.service.retrieval;

import java.util.List;

/** 真实模型适配边界；测试可替换，但替身不计为真实语义检索验收。 */
public interface EmbeddingClient {
  List<double[]> embed(List<String> texts, long deadlineNanos);
}
