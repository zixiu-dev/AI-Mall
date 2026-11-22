package com.bitmall.agent.knowledge.service;

import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexStore;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.model.KnowledgeVectors;
import com.bitmall.agent.knowledge.service.retrieval.EmbeddingClient;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeText;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 知识向量索引的离线构建与读取校验。
 *
 * <p>构建（rebuild）只由离线命令触发，不会在聊天请求中执行，也不作为工具暴露给模型。
 * 读取（snapshot）在每次混合检索前调用，确认已发布索引与当前配置和启用文档一致。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexService {
  private static final String BRAND_PLATFORM = "PLATFORM";
  private static final String REGION_CN_MAINLAND = "CN_MAINLAND";

  /** 整次构建中调用 embedding 模型的总时间上限。 */
  private static final long EMBEDDING_TIMEOUT_SECONDS = 120;

  /** 查询启用的政策文档，并提供"锁定文档源"的事务。 */
  private final KnowledgeRepository documents;

  /** 索引存储：登记构建中的版本、写入知识块、切换发布指针。 */
  private final KnowledgeIndexStore indexes;

  /** 把知识块文本转换为向量。 */
  private final EmbeddingClient embedding;

  /** 重建索引时读取：知识块数上限、embedding 模型/版本/维度（登记到新版本并校验向量）、向量化批大小。 */
  private final KnowledgeProperties properties;

  /**
   * 重建并发布一个新的索引版本，返回版本号。
   *
   * <p>耗时的向量生成全部在事务外完成；只有在所有块都校验通过、且构建期间文档原文没有变化时，
   * 才在锁定文档源的事务内原子发布。任何一步失败都会把该版本标记为失败并重新抛出原异常。
   *
   * @throws IllegalStateException 没有启用文档、文档元数据不合法、块数超限、向量不合法或原文在构建期间变化时
   */
  public String rebuild() {
    // 第 1 步：读取启用文档，记录原文指纹，并切分成知识块。
    List<KnowledgeHitVO> enabledDocuments = documents.listEnabled();
    if (enabledDocuments.isEmpty()) {
      throw new IllegalStateException("没有启用知识文档");
    }
    Map<String, String> sourceHashesAtStart = hashByDocumentKey(enabledDocuments);
    List<KnowledgeChunk> chunks = new ArrayList<>();
    List<String> embeddingInputs = new ArrayList<>();
    for (KnowledgeHitVO document : enabledDocuments) {
      ensurePublishable(document);
      int ordinal = 0;
      for (String text : KnowledgeText.chunks(document.getContent())) {
        ordinal++;
        String chunkId = document.getDocumentKey() + "-" + ordinal;
        chunks.add(
            new KnowledgeChunk(
                chunkId,
                KnowledgeText.documentHash(document),
                KnowledgeText.hash(text),
                document.getTitle(),
                KnowledgeText.withContent(document, text, 0),
                new double[0]));
        // 向量化的输入是"文档标题 + 换行 + 块正文"，与 chunks 按下标一一对应。
        embeddingInputs.add(document.getTitle() + "\n" + text);
      }
    }
    if (chunks.size() > properties.getMaximumChunks()) {
      throw new IllegalStateException("索引超过小库精确扫描上限");
    }

    // 第 2 步：登记一个构建中的新版本。
    String version = "knowledge-" + UUID.randomUUID();
    indexes.begin(
        version,
        properties.getEmbeddingModel(),
        properties.getEmbeddingVersion(),
        properties.getEmbeddingDimension(),
        enabledDocuments.size());
    try {
      // 第 3 步：分批生成向量并挂到对应知识块上。
      List<double[]> vectors = embedInBatches(embeddingInputs);
      if (vectors.size() != chunks.size()) {
        throw new IllegalStateException("向量数量与原文块不符");
      }
      List<KnowledgeChunk> chunksWithVectors = attachVectors(chunks, vectors);

      // 第 4 步：锁定文档源后发布；发布前后都会核对原文是否仍与构建开始时一致。
      documents.withLockedSource(
          () ->
              indexes.publish(
                  version,
                  chunksWithVectors,
                  () -> {
                    if (!sourceHashesAtStart.equals(sourceHashes())) {
                      throw new IllegalStateException("构建期间知识原文变化，请重建");
                    }
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

  /**
   * 读取当前已发布的索引快照，并确认它仍可用于检索。
   *
   * <p>以下任一情况都会抛异常，调用方据此降级为纯关键词检索：
   * embedding 模型、版本或维度与当前配置不一致；某个块的原文已经变化；
   * 索引覆盖的文档集合与当前启用文档不一致。
   */
  public KnowledgeIndexSnapshot snapshot() {
    KnowledgeIndexSnapshot snapshot = indexes.published(properties.getMaximumChunks());
    boolean modelMatches =
        snapshot.getModelId().equals(properties.getEmbeddingModel())
            && snapshot.getModelVersion().equals(properties.getEmbeddingVersion())
            && snapshot.getDimension() == properties.getEmbeddingDimension();
    if (!modelMatches) {
      throw new IllegalStateException("发布索引的模型、版本或维度不兼容");
    }

    Map<String, String> expected = sourceHashes();
    Map<String, String> indexed = new HashMap<>();
    for (KnowledgeChunk chunk : snapshot.getChunks()) {
      KnowledgeHitVO document = chunk.getDocument();
      boolean documentUnchanged =
          chunk.getDocumentHash().equals(expected.get(document.getDocumentKey()));
      boolean textUnchanged = chunk.getTextHash().equals(KnowledgeText.hash(document.getContent()));
      if (!documentUnchanged || !textUnchanged) {
        throw new IllegalStateException("知识内容已变化，索引必须重建");
      }
      // 数据库向量检索不会把向量读到 JVM，只有内存检索模式需要在这里校验向量。
      if (!snapshot.isDatabaseVectorSearch()) {
        KnowledgeVectors.validate(chunk.getVector(), snapshot.getDimension());
      }
      indexed.put(document.getDocumentKey(), chunk.getDocumentHash());
    }
    if (!indexed.equals(expected)) {
      throw new IllegalStateException("发布索引未覆盖当前启用文档");
    }
    return snapshot;
  }

  /**
   * 文档必须有身份（documentKey、contentVersion）、来源名称和中国大陆地区；
   * 品牌文档还必须有 https 来源链接，本站规则文档可以没有链接。
   */
  private static void ensurePublishable(KnowledgeHitVO document) {
    boolean hasIdentity =
        document.getDocumentKey() != null
            && document.getContentVersion() != null
            && document.getSourceName() != null;
    boolean isPlatformRule = BRAND_PLATFORM.equals(document.getBrand());
    boolean hasHttpsSource =
        document.getSourceUrl() != null && document.getSourceUrl().startsWith("https://");
    boolean sourceValid = isPlatformRule || hasHttpsSource;
    boolean regionValid = REGION_CN_MAINLAND.equals(document.getRegion());
    if (!hasIdentity || !sourceValid || !regionValid) {
      throw new IllegalStateException("知识文档身份、地区或来源不合法");
    }
  }

  /** 按配置的批大小分批调用 embedding，所有批次共享同一个截止时间。 */
  private List<double[]> embedInBatches(List<String> texts) {
    List<double[]> vectors = new ArrayList<>();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EMBEDDING_TIMEOUT_SECONDS);
    int batchSize = properties.getEmbeddingBatchSize();
    for (int start = 0; start < texts.size(); start += batchSize) {
      int end = Math.min(start + batchSize, texts.size());
      vectors.addAll(embedding.embed(texts.subList(start, end), deadline));
    }
    return vectors;
  }

  /** 校验每个向量的维度和数值，并生成带向量的知识块副本（顺序与输入一致）。 */
  private List<KnowledgeChunk> attachVectors(List<KnowledgeChunk> chunks, List<double[]> vectors) {
    List<KnowledgeChunk> result = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      KnowledgeChunk chunk = chunks.get(i);
      double[] vector = vectors.get(i);
      KnowledgeVectors.validate(vector, properties.getEmbeddingDimension());
      result.add(
          new KnowledgeChunk(
              chunk.getChunkId(),
              chunk.getDocumentHash(),
              chunk.getTextHash(),
              chunk.getSectionTitle(),
              chunk.getDocument(),
              vector));
    }
    return result;
  }

  /** 当前启用文档的 documentKey → 原文指纹。 */
  private Map<String, String> sourceHashes() {
    return hashByDocumentKey(documents.listEnabled());
  }

  private static Map<String, String> hashByDocumentKey(List<KnowledgeHitVO> documents) {
    return documents.stream()
        .collect(Collectors.toMap(document -> document.getDocumentKey(), KnowledgeText::documentHash));
  }
}
