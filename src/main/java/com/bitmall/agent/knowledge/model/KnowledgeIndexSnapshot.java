package com.bitmall.agent.knowledge.model;

import java.util.List;
import lombok.Getter;

/** 一次检索固定的已发布索引身份，不能在不同召回支路间悄悄切换。 */
@Getter
public final class KnowledgeIndexSnapshot {
  private final String indexVersion;
  private final String modelId;
  private final String modelVersion;
  private final int dimension;
  private final boolean databaseVectorSearch;
  private final List<KnowledgeChunk> chunks;

  public KnowledgeIndexSnapshot(
      String indexVersion,
      String modelId,
      String modelVersion,
      int dimension,
      List<KnowledgeChunk> chunks) {
    this(indexVersion, modelId, modelVersion, dimension, chunks, false);
  }

  public KnowledgeIndexSnapshot(
      String indexVersion,
      String modelId,
      String modelVersion,
      int dimension,
      List<KnowledgeChunk> chunks,
      boolean databaseVectorSearch) {
    this.databaseVectorSearch = databaseVectorSearch;
    this.indexVersion = indexVersion;
    this.modelId = modelId;
    this.modelVersion = modelVersion;
    this.dimension = dimension;
    this.chunks = List.copyOf(chunks);
  }
}
