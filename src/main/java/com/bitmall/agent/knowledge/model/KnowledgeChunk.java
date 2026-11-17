package com.bitmall.agent.knowledge.model;

import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;

/** 已索引的完整证据单元，绑定原文哈希与模型向量；不是业务事实或生成答案。 */
public final class KnowledgeChunk {
  private final String chunkId, documentHash, textHash, sectionTitle;
  private final KnowledgeHitVO document;
  private final double[] vector;

  public KnowledgeChunk(
      String chunkId,
      String documentHash,
      String textHash,
      String sectionTitle,
      KnowledgeHitVO document,
      double[] vector) {
    this.chunkId = chunkId;
    this.documentHash = documentHash;
    this.textHash = textHash;
    this.sectionTitle = sectionTitle;
    this.document = document;
    this.vector = vector.clone();
  }

  public String getChunkId() {
    return chunkId;
  }

  public String getDocumentHash() {
    return documentHash;
  }

  public String getTextHash() {
    return textHash;
  }

  public String getSectionTitle() {
    return sectionTitle;
  }

  public KnowledgeHitVO getDocument() {
    return document;
  }

  public double[] getVector() {
    return vector.clone();
  }
}
