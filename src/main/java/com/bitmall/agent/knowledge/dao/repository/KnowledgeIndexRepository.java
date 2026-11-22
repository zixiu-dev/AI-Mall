package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.dao.mapper.KnowledgeIndexMapper;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 索引存储与原子发布；网络调用在事务外完成，读者只沿发布指针读取。 */
@Repository
@ConditionalOnProperty(name = "app.knowledge.vector-store", havingValue = "LEGACY")
public class KnowledgeIndexRepository implements KnowledgeIndexStore {
  /** 知识块和索引版本的 SQL，定义在 mapper/knowledge/KnowledgeIndexMapper.xml。 */
  private final KnowledgeIndexMapper mapper;

  /** 知识块元数据与 JSON 文本互相转换（存入和读出索引表）。 */
  private final ObjectMapper json;

  /** 将知识块写入、版本完成和发布指针切换放进同一主库事务，失败时保留旧发布版本。 */
  private final TransactionTemplate tx;

  public KnowledgeIndexRepository(
      KnowledgeIndexMapper mapper, ObjectMapper json, PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
  }

  public void begin(
      String version, String model, String modelVersion, int dimension, int documents) {
    mapper.begin(
        Map.of(
            "version",
            version,
            "model",
            model,
            "modelVersion",
            modelVersion,
            "dimension",
            dimension,
            "documents",
            documents));
  }

  public void fail(String version) {
    mapper.fail(version);
  }

  /** 锁定发布指针和源文档后复核快照，证据块与新指针在同一事务提交。 */
  public void publish(String version, List<KnowledgeChunk> chunks, Runnable verifySource) {
    tx.executeWithoutResult(
        status -> {
          if (mapper.lockHead() == null) {
            throw new IllegalStateException("索引发布指针不存在");
          }
          mapper.lockDocuments();
          verifySource.run();
          for (KnowledgeChunk chunk : chunks) {
            insertChunk(version, chunk);
          }
          if (mapper.complete(version, chunks.size()) != 1) {
            throw new IllegalStateException("候选索引已不处于构建状态");
          }
          if (mapper.switchHead(version) != 1) {
            throw new IllegalStateException("发布指针切换失败");
          }
        });
  }

  /** 将一个知识块的向量和来源序列化后写入候选版本；失败向外抛出，触发发布事务回滚。 */
  private void insertChunk(String version, KnowledgeChunk chunk) {
    try {
      KnowledgeHitVO document = chunk.getDocument();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("version", version);
      row.put("chunkId", chunk.getChunkId());
      row.put("documentKey", document.getDocumentKey());
      row.put("contentVersion", document.getContentVersion());
      row.put("documentHash", chunk.getDocumentHash());
      row.put("textHash", chunk.getTextHash());
      row.put("sectionTitle", chunk.getSectionTitle());
      row.put("content", document.getContent());
      row.put("vector", json.writeValueAsString(chunk.getVector()));
      row.put("metadata", json.writeValueAsString(document));
      row.put("brand", document.getBrand());
      row.put("type", document.getDocumentType());
      row.put("region", document.getRegion());
      mapper.insertChunk(row);
    } catch (Exception error) {
      throw new IllegalStateException("索引块写入失败", error);
    }
  }

  /** 读取有界的小型索引快照；规模超限必须改用专门向量存储，不能无限加载 JVM。 */
  public KnowledgeIndexSnapshot published(int maximum) {
    Map<String, Object> versionRow = mapper.published();
    if (versionRow == null) {
      throw new IllegalStateException("没有已发布的向量索引，请离线构建");
    }
    String version = RowValues.string(versionRow, "index_version");
    List<KnowledgeChunk> chunks =
        mapper.chunks(version, maximum + 1).stream().map(this::chunk).toList();
    boolean invalidChunkCount =
        chunks.size() > maximum || chunks.size() != RowValues.intValue(versionRow, "chunk_count");
    if (invalidChunkCount) {
      throw new IllegalStateException("索引规模超限或块数不一致");
    }
    return new KnowledgeIndexSnapshot(
        version,
        RowValues.string(versionRow, "embedding_model_id"),
        RowValues.string(versionRow, "embedding_version"),
        RowValues.intValue(versionRow, "dimension"),
        chunks);
  }

  public List<Map<String, Object>> search(
      String version, String query, String brand, String type, int limit) {
    return mapper.searchChunks(
        Map.of(
            "version",
            version,
            "brand",
            brand,
            "type",
            type,
            "limit",
            limit,
            "patterns",
            KnowledgeRepository.patterns(query)));
  }

  /** 还原完整来源及向量，损坏记录直接报错，避免把缺失证据当成正常索引。 */
  private KnowledgeChunk chunk(Map<String, Object> row) {
    try {
      return new KnowledgeChunk(
          RowValues.string(row, "chunk_id"),
          RowValues.string(row, "document_hash"),
          RowValues.string(row, "text_hash"),
          RowValues.string(row, "section_title"),
          json.readValue(RowValues.string(row, "metadata_json"), KnowledgeHitVO.class),
          json.readValue(RowValues.string(row, "vector_json"), double[].class));
    } catch (Exception error) {
      throw new IllegalStateException("索引块损坏", error);
    }
  }
}
