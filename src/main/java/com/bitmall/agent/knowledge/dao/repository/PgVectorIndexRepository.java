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
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 PostgreSQL + pgvector 的知识索引存储，向量检索在数据库内完成，不把向量读到 JVM 里扫描。
 *
 * <p>使用独立的连接池、独立的 Flyway 迁移和独立的 MyBatis 配置，不复用也不影响商城主库的 DataSource。
 * 连接池在第一次使用时才创建，所以纯 KEYWORD 模式下不会连接向量库。
 */
@Repository
@ConditionalOnProperty(
    name = "app.knowledge.vector-store",
    havingValue = "PGVECTOR",
    matchIfMissing = true)
@RequiredArgsConstructor
public class PgVectorIndexRepository implements KnowledgeIndexStore {
  /** 当前 schema 中向量列的类型是 vector(1024)，维度写死在迁移脚本里。 */
  private static final int VECTOR_DIMENSION = 1024;

  /** pgvector 检索方式：EXACT 精确扫描；HNSW 近似索引，结果不足时回退到精确扫描。 */
  private static final String SEARCH_MODE_EXACT = "EXACT";

  private static final String SEARCH_MODE_HNSW = "HNSW";

  /** 向量库连接参数和检索方式配置。 */
  private final PgVectorProperties properties;

  /** 旧库校验所需的可选外部 Flyway 脚本目录。 */
  @Value("${app.database.history-dir}")
  private final String databaseHistoryDir;

  /** 序列化向量、文档元数据，以及反序列化 metadata_json。 */
  private final ObjectMapper json;

  /** 初始化成功后的连接池，关闭时释放。 */
  private HikariDataSource pool;

  private volatile SqlSessionFactory factory;

  /**
   * 懒加载 MyBatis 会话工厂：首次调用时建连接池、执行 Flyway 迁移、加载 Mapper XML。
   *
   * <p>任何一步失败都会关闭刚建的连接池并抛异常，不缓存失败状态，下一次请求会重新尝试初始化。
   */
  private synchronized SqlSessionFactory sessions() {
    if (factory != null) {
      return factory;
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(properties.getUrl());
    config.setUsername(properties.getUsername());
    config.setPassword(properties.getPassword());
    config.setDriverClassName("org.postgresql.Driver");
    config.setPoolName("knowledge-pgvector");
    // 连接/校验超时单位为毫秒；initializationFailTimeout=-1 表示创建池时不预先建连接，失败留到首次使用时暴露。
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(2000);
    config.setValidationTimeout(1000);
    config.setInitializationFailTimeout(-1);
    // PostgreSQL JDBC 驱动的 connectTimeout / socketTimeout 单位为秒。
    config.addDataSourceProperty("connectTimeout", 2);
    config.addDataSourceProperty("currentSchema", "knowledge,public");
    config.addDataSourceProperty("socketTimeout", 6);
    config.addDataSourceProperty("ApplicationName", "bitmall-knowledge");
    HikariDataSource candidate = new HikariDataSource(config);
    try {
      Flyway.configure()
          .dataSource(candidate)
          .locations("classpath:db/pgvector", "filesystem:" + databaseHistoryDir + "/pgvector")
          .schemas("knowledge")
          .defaultSchema("knowledge")
          .table("knowledge_flyway_schema_history")
          .connectRetries(0)
          .load()
          .migrate();
      Configuration mybatis =
          new Configuration(
              new Environment("knowledge-pgvector", new JdbcTransactionFactory(), candidate));
      mybatis.setDefaultStatementTimeout(5);
      mybatis.setLocalCacheScope(LocalCacheScope.STATEMENT);
      ClassPathResource resource = new ClassPathResource("pgvector/PgVectorIndexMapper.xml");
      try (InputStream in = resource.getInputStream()) {
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

  /**
   * 在一个独立的 PG 事务中执行一次仓库操作：先设置语句超时和锁超时，成功则提交，抛异常则回滚。
   *
   * <p>每次操作独占一个事务，是为了让 HNSW 检索用到的 SET LOCAL 参数随事务结束而失效，
   * 不会残留到连接池里下一个借用该连接的调用者。
   */
  private <T> T execute(Function<PgVectorIndexMapper, T> operation) {
    try (SqlSession session = sessions().openSession(false)) {
      try {
        PgVectorIndexMapper mapper = session.getMapper(PgVectorIndexMapper.class);
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
    if (dimension != VECTOR_DIMENSION) {
      throw new IllegalArgumentException("当前 pgvector schema 为 vector(1024)，更换维度须新增迁移并重建索引");
    }
    execute(
        mapper ->
            mapper.begin(
                Map.of(
                    "version", version,
                    "model", model,
                    "modelVersion", modelVersion,
                    "dimension", dimension,
                    "documents", documents)));
  }

  @Override
  public void fail(String version) {
    execute(mapper -> mapper.fail(version));
  }

  /**
   * 在一个事务内写入全部知识块并切换发布指针。
   *
   * <p>先用 SELECT ... FOR UPDATE 锁住发布指针，避免两次发布并发交错；写入前后各调用一次 verifySource，
   * 确认构建期间文档原文没有变化。版本状态更新或指针切换没有恰好影响 1 行时视为并发冲突，整个事务回滚。
   */
  @Override
  public void publish(String version, List<KnowledgeChunk> chunks, Runnable verifySource) {
    execute(
        mapper -> {
          if (mapper.lockHead() == null) {
            throw new IllegalStateException("PG 发布指针缺失");
          }
          verifySource.run();
          for (KnowledgeChunk chunk : chunks) {
            KnowledgeVectors.validate(chunk.getVector(), VECTOR_DIMENSION);
            mapper.insertChunk(chunkRow(version, chunk));
          }
          verifySource.run();
          if (mapper.complete(version, chunks.size()) != 1) {
            throw new IllegalStateException("PG 发布状态冲突");
          }
          if (mapper.switchHead(version) != 1) {
            throw new IllegalStateException("PG 发布状态冲突");
          }
          return null;
        });
  }

  /** 组装 insertChunk 的参数；向量和文档元数据以 JSON 字符串传入，由 SQL 转成 vector / jsonb。 */
  private Map<String, Object> chunkRow(String version, KnowledgeChunk chunk) {
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
    row.put("vector", encode(chunk.getVector()));
    row.put("metadata", encode(document));
    row.put("brand", document.getBrand());
    row.put("type", document.getDocumentType());
    row.put("region", document.getRegion());
    return row;
  }

  /**
   * 读取当前已发布版本及其全部知识块元数据（不含向量，向量检索由数据库完成）。
   *
   * <p>多查一行（maximum + 1）用来判断是否超出上限；块数还必须与版本记录的 chunk_count 一致，
   * 否则说明索引数据不完整。
   */
  @Override
  public KnowledgeIndexSnapshot published(int maximum) {
    return execute(
        mapper -> {
          Map<String, Object> versionRow = mapper.published();
          if (versionRow == null) {
            throw new IllegalStateException("PG 尚无已发布索引，请离线重建");
          }
          String version = RowValues.string(versionRow, "index_version");
          List<Map<String, Object>> rows = mapper.chunks(version, maximum + 1);
          boolean tooMany = rows.size() > maximum;
          boolean countMismatch = rows.size() != RowValues.intValue(versionRow, "chunk_count");
          if (tooMany || countMismatch) {
            throw new IllegalStateException("PG 索引规模超限或块数不符");
          }
          List<KnowledgeChunk> chunks = rows.stream().map(this::metadata).toList();
          return new KnowledgeIndexSnapshot(
              version,
              RowValues.string(versionRow, "embedding_model_id"),
              RowValues.string(versionRow, "embedding_version"),
              RowValues.intValue(versionRow, "dimension"),
              chunks,
              true);
        });
  }

  @Override
  public List<Map<String, Object>> search(
      String version, String query, String brand, String type, int limit) {
    Map<String, Object> args = scope(version, brand, type, limit);
    args.put("patterns", KnowledgeRepository.patterns(query));
    return execute(mapper -> mapper.keyword(args));
  }

  @Override
  public List<Map<String, Object>> vectorSearch(
      String version, double[] vector, String brand, String type, int limit) {
    KnowledgeVectors.validate(vector, VECTOR_DIMENSION);
    Map<String, Object> args = scope(version, brand, type, limit);
    args.put("vector", encode(vector));
    return execute(
        mapper -> {
          String searchMode = properties.getSearchMode();
          if (SEARCH_MODE_EXACT.equals(searchMode)) {
            return mapper.exact(args);
          }
          if (!SEARCH_MODE_HNSW.equals(searchMode)) {
            throw new IllegalStateException("PG 检索模式无效");
          }
          // HNSW 是近似检索，带过滤条件时可能返回不足 limit 条；
          // 少于"limit 与过滤范围内总块数"中的较小值时，改用精确扫描保证召回完整。
          mapper.setIterativeScan();
          mapper.setEfSearch();
          List<Map<String, Object>> rows = mapper.ann(args);
          int expected = Math.min(limit, mapper.scopeCount(args));
          if (rows.size() < expected) {
            return mapper.exact(args);
          }
          return rows;
        });
  }

  /** 检索公共参数：索引版本 + 品牌/类别过滤 + 候选数（1～10）。返回可变 Map，调用方会继续追加参数。 */
  private LinkedHashMap<String, Object> scope(
      String version, String brand, String type, int limit) {
    if (limit < 1 || limit > 10) {
      throw new IllegalArgumentException("向量候选数应为 1～10");
    }
    LinkedHashMap<String, Object> args = new LinkedHashMap<>();
    args.put("version", version);
    args.put("brand", brand);
    args.put("type", type);
    args.put("limit", limit);
    return args;
  }

  /** 把 chunks 查询的一行转换成知识块；向量不读回 JVM，置为空数组。 */
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
