package com.bitmall.agent.knowledge.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.config.PgVectorProperties;
import com.bitmall.agent.knowledge.dao.mapper.pgvector.PgVectorIndexMapper;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.model.KnowledgeVectors;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL 原生向量存储；独立池与 MyBatis，不接管商城 DataSource，也不在 JVM 扫描向量。 */
@Repository
@ConditionalOnProperty(
    name = "app.knowledge.vector-store",
    havingValue = "PGVECTOR",
    matchIfMissing = true)
public class PgVectorIndexRepository implements KnowledgeIndexStore {
  private final PgVectorProperties properties;
  private final ObjectMapper json;
  private HikariDataSource pool;
  private volatile SqlSessionFactory factory;

  public PgVectorIndexRepository(PgVectorProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
  }

  /** KEYWORD 不访问这里。连接或迁移失败时销毁候选池，下次请求允许重试。 */
  private synchronized SqlSessionFactory sessions() {
    if (factory != null) return factory;
    var config = new HikariConfig();
    config.setJdbcUrl(properties.getUrl());
    config.setUsername(properties.getUsername());
    config.setPassword(properties.getPassword());
    config.setDriverClassName("org.postgresql.Driver");
    config.setPoolName("knowledge-pgvector");
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(2000);
    config.setValidationTimeout(1000);
    config.setInitializationFailTimeout(-1);
    config.addDataSourceProperty("connectTimeout", 2);
    config.addDataSourceProperty("currentSchema", "knowledge,public");
    config.addDataSourceProperty("socketTimeout", 6);
    config.addDataSourceProperty("ApplicationName", "bitmall-knowledge");
    var candidate = new HikariDataSource(config);
    try {
      Flyway.configure()
          .dataSource(candidate)
          .locations("classpath:db/pgvector")
          .schemas("knowledge")
          .defaultSchema("knowledge")
          .table("knowledge_flyway_schema_history")
          .connectRetries(0)
          .load()
          .migrate();
      var mybatis =
          new Configuration(
              new Environment("knowledge-pgvector", new JdbcTransactionFactory(), candidate));
      mybatis.setDefaultStatementTimeout(5);
      mybatis.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
      var resource = new ClassPathResource("pgvector/PgVectorIndexMapper.xml");
      try (var in = resource.getInputStream()) {
        new XMLMapperBuilder(in, mybatis, resource.toString(), mybatis.getSqlFragments()).parse();
      }
      factory = new SqlSessionFactoryBuilder().build(mybatis);
      pool = candidate;
      return factory;
    } catch (Exception e) {
      candidate.close();
      throw new IllegalStateException("PostgreSQL 向量库连接或迁移失败", e);
    }
  }

  /** 每次仓库操作拥有独立 PG 事务；包括 ANN 的 SET LOCAL，不污染连接池下一位调用者。 */
  private <T> T execute(Function<PgVectorIndexMapper, T> operation) {
    try (var session = sessions().openSession(false)) {
      try {
        var mapper = session.getMapper(PgVectorIndexMapper.class);
        mapper.setStatementTimeout();
        mapper.setLockTimeout();
        T value = operation.apply(mapper);
        session.commit();
        return value;
      } catch (RuntimeException e) {
        session.rollback();
        throw e;
      }
    }
  }

  @Override
  public void begin(
      String version, String model, String modelVersion, int dimension, int documents) {
    if (dimension != 1024)
      throw new IllegalArgumentException("当前 pgvector schema 为 vector(1024)，更换维度须新增迁移并重建索引");
    execute(
        m ->
            m.begin(
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
                    documents)));
  }

  @Override
  public void fail(String version) {
    execute(m -> m.fail(version));
  }

  @Override
  public void publish(String version, List<KnowledgeChunk> chunks, Runnable verifySource) {
    execute(
        mapper -> {
          if (mapper.lockHead() == null) throw new IllegalStateException("PG 发布指针缺失");
          verifySource.run();
          for (var c : chunks) {
            var d = c.getDocument();
            KnowledgeVectors.validate(c.getVector(), 1024);
            var row = new LinkedHashMap<String, Object>();
            row.put("version", version);
            row.put("chunkId", c.getChunkId());
            row.put("documentKey", d.getDocumentKey());
            row.put("contentVersion", d.getContentVersion());
            row.put("documentHash", c.getDocumentHash());
            row.put("textHash", c.getTextHash());
            row.put("sectionTitle", c.getSectionTitle());
            row.put("content", d.getContent());
            row.put("vector", encode(c.getVector()));
            row.put("metadata", encode(d));
            row.put("brand", d.getBrand());
            row.put("type", d.getDocumentType());
            row.put("region", d.getRegion());
            mapper.insertChunk(row);
          }
          verifySource.run();
          if (mapper.complete(version, chunks.size()) != 1 || mapper.switchHead(version) != 1)
            throw new IllegalStateException("PG 发布状态冲突");
          return null;
        });
  }

  @Override
  public KnowledgeIndexSnapshot published(int maximum) {
    return execute(
        m -> {
          var v = m.published();
          if (v == null) throw new IllegalStateException("PG 尚无已发布索引，请离线重建");
          String version = RowValues.string(v, "index_version");
          var rows = m.chunks(version, maximum + 1);
          if (rows.size() > maximum || rows.size() != RowValues.intValue(v, "chunk_count"))
            throw new IllegalStateException("PG 索引规模超限或块数不符");
          var chunks = rows.stream().map(this::metadata).toList();
          return new KnowledgeIndexSnapshot(
              version,
              RowValues.string(v, "embedding_model_id"),
              RowValues.string(v, "embedding_version"),
              RowValues.intValue(v, "dimension"),
              chunks,
              true);
        });
  }

  @Override
  public List<Map<String, Object>> search(
      String version, String query, String brand, String type, int limit) {
    var args = scope(version, brand, type, limit);
    args.put("patterns", KnowledgeRepository.patterns(query));
    return execute(m -> m.keyword(args));
  }

  @Override
  public List<Map<String, Object>> vectorSearch(
      String version, double[] vector, String brand, String type, int limit) {
    KnowledgeVectors.validate(vector, 1024);
    var args = scope(version, brand, type, limit);
    args.put("vector", encode(vector));
    return execute(
        m -> {
          if ("EXACT".equals(properties.getSearchMode())) return m.exact(args);
          if (!"HNSW".equals(properties.getSearchMode()))
            throw new IllegalStateException("PG 检索模式无效");
          m.setIterativeScan();
          m.setEfSearch();
          var rows = m.ann(args);
          int expected = Math.min(limit, m.scopeCount(args));
          return rows.size() < expected ? m.exact(args) : rows;
        });
  }

  private LinkedHashMap<String, Object> scope(
      String version, String brand, String type, int limit) {
    if (limit < 1 || limit > 10) throw new IllegalArgumentException("向量候选数应为 1～10");
    var map = new LinkedHashMap<String, Object>();
    map.put("version", version);
    map.put("brand", brand);
    map.put("type", type);
    map.put("limit", limit);
    return map;
  }

  private KnowledgeChunk metadata(Map<String, Object> row) {
    try {
      return new KnowledgeChunk(
          RowValues.string(row, "chunk_id"),
          RowValues.string(row, "document_hash"),
          RowValues.string(row, "text_hash"),
          RowValues.string(row, "section_title"),
          json.readValue(RowValues.string(row, "metadata_json"), KnowledgeHitVO.class),
          new double[0]);
    } catch (Exception e) {
      throw new IllegalStateException("PG chunk 元数据损坏", e);
    }
  }

  private String encode(Object data) {
    try {
      return json.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException("PG 知识数据序列化失败", e);
    }
  }

  @PreDestroy
  public synchronized void close() {
    factory = null;
    if (pool != null) {
      pool.close();
      pool = null;
    }
  }
}
