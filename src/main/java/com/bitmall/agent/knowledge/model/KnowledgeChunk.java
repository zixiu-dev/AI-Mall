package com.bitmall.agent.knowledge.model;

import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import lombok.Getter;

/**
 * 已写入索引的一个知识块：一段可被引用的政策原文，加上它的向量。
 *
 * <p>同时记录所属文档和原文的哈希，检索时据此确认索引与当前文档一致。它只是检索证据，不是业务事实，也不是生成的答案。
 */
public final class KnowledgeChunk {
  /** 知识块的稳定编号。 */
  @Getter private final String chunkId;

  /** 生成该块时所属文档的内容哈希，用于发现文档已改动、索引过期。 */
  @Getter private final String documentHash;

  /** 该块正文的哈希。 */
  @Getter private final String textHash;

  /** 该块所在的章节标题。 */
  @Getter private final String sectionTitle;

  /** 所属文档的完整信息（品牌、类别、出处等）。 */
  @Getter private final KnowledgeHitVO document;

  /** embedding 向量。数组是可变的，进出都做拷贝，防止外部修改。 */
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

  public double[] getVector() {
    return vector.clone();
  }
}
