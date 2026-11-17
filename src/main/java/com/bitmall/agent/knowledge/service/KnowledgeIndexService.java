package com.bitmall.agent.knowledge.service;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexStore;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.service.retrieval.EmbeddingClient;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeText;
import com.bitmall.agent.knowledge.service.retrieval.SpringAiEmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 离线索引构建与查询版本验证；不在聊天请求中构建，不向模型暴露发布能力。 */
@Service
public class KnowledgeIndexService {
  private final KnowledgeRepository documents;
  private final KnowledgeIndexStore indexes;
  private final EmbeddingClient embedding;
  private final KnowledgeProperties properties;

  public KnowledgeIndexService(
      KnowledgeRepository documents,
      KnowledgeIndexStore indexes,
      EmbeddingClient embedding,
      KnowledgeProperties properties) {
    this.documents = documents;
    this.indexes = indexes;
    this.embedding = embedding;
    this.properties = properties;
  }

  /** 先在事务外生成全部向量；只在原文未变且所有块校验通过时原子发布。 */
  public String rebuild() {
    var docs = documents.listEnabled();
    if (docs.isEmpty()) throw new IllegalStateException("没有启用知识文档");
    var source =
        docs.stream()
            .collect(Collectors.toMap(d -> d.getDocumentKey(), KnowledgeText::documentHash));
    var chunks = new ArrayList<KnowledgeChunk>();
    var texts = new ArrayList<String>();
    for (var d : docs) {
      if (d.getDocumentKey() == null
          || d.getContentVersion() == null
          || d.getSourceName() == null
          || (!"PLATFORM".equals(d.getBrand())
              && (d.getSourceUrl() == null || !d.getSourceUrl().startsWith("https://")))
          || !"CN_MAINLAND".equals(d.getRegion()))
        throw new IllegalStateException("知识文档身份、地区或来源不合法");
      int i = 0;
      for (String text : KnowledgeText.chunks(d.getContent())) {
        String id = d.getDocumentKey() + "-" + (++i);
        chunks.add(
            new KnowledgeChunk(
                id,
                KnowledgeText.documentHash(d),
                KnowledgeText.hash(text),
                d.getTitle(),
                KnowledgeText.withContent(d, text, 0),
                new double[0]));
        texts.add(d.getTitle() + "\n" + text);
      }
    }
    if (chunks.size() > properties.getMaximumChunks())
      throw new IllegalStateException("索引超过小库精确扫描上限");
    String version = "knowledge-" + UUID.randomUUID();
    indexes.begin(
        version,
        properties.getEmbeddingModel(),
        properties.getEmbeddingVersion(),
        properties.getEmbeddingDimension(),
        docs.size());
    try {
      List<double[]> vectors = new ArrayList<>();
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
      for (int start = 0; start < texts.size(); start += properties.getEmbeddingBatchSize())
        vectors.addAll(
            embedding.embed(
                texts.subList(
                    start, Math.min(start + properties.getEmbeddingBatchSize(), texts.size())),
                deadline));
      if (vectors.size() != chunks.size()) throw new IllegalStateException("向量数量与原文块不符");
      var ready = new ArrayList<KnowledgeChunk>();
      for (int i = 0; i < chunks.size(); i++) {
        var c = chunks.get(i);
        com.bitmall.agent.knowledge.model.KnowledgeVectors.validate(vectors.get(i), properties.getEmbeddingDimension());
        ready.add(
            new KnowledgeChunk(
                c.getChunkId(),
                c.getDocumentHash(),
                c.getTextHash(),
                c.getSectionTitle(),
                c.getDocument(),
                vectors.get(i)));
      }
      documents.withLockedSource(
          () ->
              indexes.publish(
                  version,
                  ready,
                  () -> {
                    if (!source.equals(sourceHashes()))
                      throw new IllegalStateException("构建期间知识原文变化，请重建");
                  }));
      return version;
    } catch (RuntimeException e) {
      try {
        indexes.fail(version);
      } catch (RuntimeException failure) {
        e.addSuppressed(failure);
      }
      throw e;
    }
  }

  public KnowledgeIndexSnapshot snapshot() {
    var snapshot = indexes.published(properties.getMaximumChunks());
    if (!snapshot.getModelId().equals(properties.getEmbeddingModel())
        || !snapshot.getModelVersion().equals(properties.getEmbeddingVersion())
        || snapshot.getDimension() != properties.getEmbeddingDimension())
      throw new IllegalStateException("发布索引的模型、版本或维度不兼容");
    var expected = sourceHashes();
    var indexed = new java.util.HashMap<String, String>();
    for (var c : snapshot.getChunks()) {
      var d = c.getDocument();
      if (!c.getDocumentHash().equals(expected.get(d.getDocumentKey()))
          || !c.getTextHash().equals(KnowledgeText.hash(d.getContent())))
        throw new IllegalStateException("知识内容已变化，索引必须重建");
      if (!snapshot.isDatabaseVectorSearch())
        com.bitmall.agent.knowledge.model.KnowledgeVectors.validate(c.getVector(), snapshot.getDimension());
      indexed.put(d.getDocumentKey(), c.getDocumentHash());
    }
    if (!indexed.equals(expected)) throw new IllegalStateException("发布索引未覆盖当前启用文档");
    return snapshot;
  }

  private Map<String, String> sourceHashes() {
    return documents.listEnabled().stream()
        .collect(Collectors.toMap(d -> d.getDocumentKey(), KnowledgeText::documentHash));
  }
}
