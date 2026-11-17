package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.dao.mapper.KnowledgeIndexMapper;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 索引存储与原子发布；网络调用在事务外完成，读者只沿发布指针读取。 */
@Repository
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.knowledge.vector-store",
    havingValue = "LEGACY")
public class KnowledgeIndexRepository implements KnowledgeIndexStore {
  private final KnowledgeIndexMapper mapper;
  private final ObjectMapper json;
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
          if (mapper.lockHead() == null) throw new IllegalStateException("索引发布指针不存在");
          mapper.lockDocuments();
          verifySource.run();
          for (var c : chunks)
            try {
              var d = c.getDocument();
              var row = new LinkedHashMap<String, Object>();
              row.put("version", version);
              row.put("chunkId", c.getChunkId());
              row.put("documentKey", d.getDocumentKey());
              row.put("contentVersion", d.getContentVersion());
              row.put("documentHash", c.getDocumentHash());
              row.put("textHash", c.getTextHash());
              row.put("sectionTitle", c.getSectionTitle());
              row.put("content", d.getContent());
              row.put("vector", json.writeValueAsString(c.getVector()));
              row.put("metadata", json.writeValueAsString(d));
              row.put("brand", d.getBrand());
              row.put("type", d.getDocumentType());
              row.put("region", d.getRegion());
              mapper.insertChunk(row);
            } catch (Exception e) {
              throw new IllegalStateException("索引块写入失败", e);
            }
          if (mapper.complete(version, chunks.size()) != 1)
            throw new IllegalStateException("候选索引已不处于构建状态");
          if (mapper.switchHead(version) != 1) throw new IllegalStateException("发布指针切换失败");
        });
  }

  /** 读取有界的小型索引快照；规模超限必须改用专门向量存储，不能无限加载 JVM。 */
  public KnowledgeIndexSnapshot published(int maximum) {
    var v = mapper.published();
    if (v == null) throw new IllegalStateException("没有已发布的向量索引，请离线构建");
    String version = RowValues.string(v, "index_version");
    var chunks = mapper.chunks(version, maximum + 1).stream().map(this::chunk).toList();
    if (chunks.size() > maximum || chunks.size() != RowValues.intValue(v, "chunk_count"))
      throw new IllegalStateException("索引规模超限或块数不一致");
    return new KnowledgeIndexSnapshot(
        version,
        RowValues.string(v, "embedding_model_id"),
        RowValues.string(v, "embedding_version"),
        RowValues.intValue(v, "dimension"),
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

  private KnowledgeChunk chunk(Map<String, Object> row) {
    try {
      return new KnowledgeChunk(
          RowValues.string(row, "chunk_id"),
          RowValues.string(row, "document_hash"),
          RowValues.string(row, "text_hash"),
          RowValues.string(row, "section_title"),
          json.readValue(RowValues.string(row, "metadata_json"), KnowledgeHitVO.class),
          json.readValue(RowValues.string(row, "vector_json"), double[].class));
    } catch (Exception e) {
      throw new IllegalStateException("索引块损坏", e);
    }
  }
}
