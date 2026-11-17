package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexStore;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.vo.KnowledgeGroupVO;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** 有界的只读召回执行器：同一版本、同一硬过滤集合内并行关键词/向量，再按排名融合。 */
@Component
public class KnowledgeRetriever {
  private final KnowledgeRepository documents;
  private final KnowledgeIndexStore indexes;
  private final KnowledgeIndexService indexService;
  private final EmbeddingClient embedding;
  private final KnowledgeProperties properties;
  private final ThreadPoolExecutor workers;

  public KnowledgeRetriever(
      KnowledgeRepository documents,
      KnowledgeIndexStore indexes,
      KnowledgeIndexService indexService,
      EmbeddingClient embedding,
      KnowledgeProperties properties) {
    this.documents = documents;
    this.indexes = indexes;
    this.indexService = indexService;
    this.embedding = embedding;
    this.properties = properties;
    int count = Math.max(1, Math.min(8, properties.getWorkers()));
    workers =
        new ThreadPoolExecutor(
            count,
            count,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            r -> {
              var t = new Thread(r, "knowledge-retrieval");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  /** 请求内缓存仍核对源内容和发布指针；源变化或新版本不能复用先前证据。 */
  public String cacheVersion() {
    String source =
        documents.listEnabled().stream()
            .map(KnowledgeText::documentHash)
            .collect(java.util.stream.Collectors.joining("|"));
    String version = "KEYWORD";
    if ("HYBRID".equals(properties.getMode()))
      try {
        version = indexes.published(properties.getMaximumChunks()).getIndexVersion();
      } catch (RuntimeException e) {
        version = "UNAVAILABLE";
      }
    return KnowledgeText.hash(
        source
            + "|"
            + version
            + "|"
            + properties.getEmbeddingModel()
            + "|"
            + properties.getEmbeddingVersion()
            + "|"
            + properties.getEmbeddingDimension());
  }

  /** 所有组先提交任务，再按共同截止时间收集；不在同一线程池嵌套阻塞等待子任务。 */
  public List<KnowledgeGroupVO> retrieve(
      List<KnowledgeQueryPlanner.Plan> plans, Map<String, List<String>> variants, long deadline) {
    boolean hybrid = "HYBRID".equals(properties.getMode());
    KnowledgeIndexSnapshot snapshot = null;
    String indexWarning = null;
    if (hybrid && plans.stream().anyMatch(p -> !p.getIntent().isClarificationRequired()))
      try {
        snapshot = bounded(indexService::snapshot, deadline);
      } catch (RuntimeException e) {
        indexWarning = "VECTOR_INDEX_UNAVAILABLE：索引未发布、过期或配置不兼容，本次仅运行数据库关键词检索";
      }
    final KnowledgeIndexSnapshot ready = snapshot;
    final String unavailable = indexWarning;
    var jobs = new LinkedHashMap<String, List<Future<List<KnowledgeHitVO>>>>();
    var submitted = new ArrayList<Future<?>>();
    try {
      for (var plan : plans) {
        if (plan.getIntent().isClarificationRequired()) continue;
        List<String> qs = variants.getOrDefault(plan.getId(), List.of(plan.getQuery()));
        Future<List<KnowledgeHitVO>> lexical = submit(() -> keyword(plan, qs, ready, deadline));
        var pair = new ArrayList<Future<List<KnowledgeHitVO>>>();
        pair.add(lexical);
        submitted.add(lexical);
        if (ready != null) {
          var vector = submit(() -> vector(plan, qs, ready, deadline));
          pair.add(vector);
          submitted.add(vector);
        }
        jobs.put(plan.getId(), pair);
      }
      var groups = new ArrayList<KnowledgeGroupVO>();
      for (var plan : plans) {
        var intent = plan.getIntent();
        var warnings = new ArrayList<String>();
        var rankings = new ArrayList<List<KnowledgeHitVO>>();
        var channels = new ArrayList<String>();
        if (intent.isClarificationRequired()) {
          groups.add(
              new KnowledgeGroupVO(
                  plan.getId(),
                  plan.getQuery(),
                  "CLARIFICATION_REQUIRED",
                  intent.getMessage(),
                  "NONE",
                  intent,
                  List.of(),
                  warnings,
                  List.of(plan.getQuery()),
                  plan.getReferences()));
          continue;
        }
        if (unavailable != null) warnings.add(unavailable);
        var tasks = jobs.get(plan.getId());
        for (int i = 0; i < tasks.size(); i++)
          try {
            rankings.add(await(tasks.get(i), deadline));
            channels.add(i == 0 ? "DB_KEYWORD" : "VECTOR");
          } catch (RuntimeException e) {
            warnings.add((i == 0 ? "DB_KEYWORD" : "VECTOR") + "_FAILED：分支超时、繁忙或服务不可用");
          }
        List<KnowledgeHitVO> hits = fuse(rankings, channels, ready);
        String mode = String.join("+", channels);
        String status = rankings.isEmpty() ? "ERROR" : hits.isEmpty() ? "NO_MATCH" : "FOUND";
        groups.add(
            new KnowledgeGroupVO(
                plan.getId(),
                plan.getQuery(),
                status,
                "ERROR".equals(status)
                    ? "检索服务不可用，不能据此认定没有政策资料。"
                    : hits.isEmpty() ? "未收录所需品牌/类别资料，不能套用其他品牌。" : intent.getMessage(),
                mode.isEmpty() ? "NONE" : mode,
                intent,
                hits,
                warnings,
                variants.getOrDefault(plan.getId(), List.of(plan.getQuery())),
                plan.getReferences()));
      }
      if (groups.stream().anyMatch(g -> "ERROR".equals(g.getStatus()))
          && groups.stream()
              .noneMatch(g -> "FOUND".equals(g.getStatus()) || "NO_MATCH".equals(g.getStatus())))
        throw new IllegalStateException("知识检索基础设施不可用，请稍后重试");
      return groups;
    } finally {
      for (var future : submitted) if (!future.isDone()) future.cancel(true);
      workers.purge();
    }
  }

  /** 增强与召回共用有界执行器；调用方只在召回任务提交之前使用。 */
  public <T> T bounded(Callable<T> call, long deadline) {
    Future<T> future = workers.submit(call);
    try {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("增强已取消");
    } catch (Exception e) {
      throw new IllegalStateException("增强超时或失败", e);
    } finally {
      if (!future.isDone()) future.cancel(true);
      workers.purge();
    }
  }

  private Future<List<KnowledgeHitVO>> submit(Callable<List<KnowledgeHitVO>> task) {
    try {
      return workers.submit(task);
    } catch (java.util.concurrent.RejectedExecutionException e) {
      var failed = new java.util.concurrent.CompletableFuture<List<KnowledgeHitVO>>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  private List<KnowledgeHitVO> await(Future<List<KnowledgeHitVO>> f, long deadline) {
    try {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0 && !f.isDone()) throw new java.util.concurrent.TimeoutException();
      return f.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      f.cancel(true);
      throw new IllegalStateException("检索已取消");
    } catch (Exception e) {
      f.cancel(true);
      throw new IllegalStateException("检索分支失败", e);
    }
  }

  private void check(long deadline) {
    if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
      throw new IllegalStateException("检索截止时间已到");
  }

  private List<KnowledgeHitVO> keyword(
      KnowledgeQueryPlanner.Plan p,
      List<String> qs,
      KnowledgeIndexSnapshot snapshot,
      long deadline) {
    var runs = new ArrayList<List<KnowledgeHitVO>>();
    String brand = p.getIntent().getBrands().get(0), type = p.getIntent().getCategories().get(0);
    for (String q : qs) {
      check(deadline);
      if (snapshot == null) {
        runs.add(documents.search(q, brand, type, properties.getCandidateLimit()));
        continue;
      }
      var chunks =
          snapshot.getChunks().stream()
              .collect(java.util.stream.Collectors.toMap(KnowledgeChunk::getChunkId, c -> c));
      runs.add(
          indexes
              .search(snapshot.getIndexVersion(), q, brand, type, properties.getCandidateLimit())
              .stream()
              .map(
                  row -> {
                    var c = chunks.get(RowValues.string(row, "chunk_id"));
                    if (c == null) throw new IllegalStateException("关键词候选不属于当前索引");
                    return decorate(
                        c, snapshot, RowValues.intValue(row, "score"), null, List.of("DB_KEYWORD"));
                  })
              .toList());
    }
    return mergeVariants(runs);
  }

  private List<KnowledgeHitVO> vector(
      KnowledgeQueryPlanner.Plan p,
      List<String> qs,
      KnowledgeIndexSnapshot snapshot,
      long deadline) {
    check(deadline);
    String brand = p.getIntent().getBrands().get(0), type = p.getIntent().getCategories().get(0);
    var legal =
        snapshot.getChunks().stream()
            .filter(
                c ->
                    brand.equals(c.getDocument().getBrand())
                        && type.equals(c.getDocument().getDocumentType())
                        && "CN_MAINLAND".equals(c.getDocument().getRegion()))
            .collect(java.util.stream.Collectors.toMap(KnowledgeChunk::getChunkId, c -> c));
    if (legal.isEmpty()) return List.of();
    var vectors = embedding.embed(qs, deadline);
    if (vectors.size() != qs.size()) throw new IllegalStateException("查询向量数量不符");
    var runs = new ArrayList<List<KnowledgeHitVO>>();
    for (var query : vectors) {
      check(deadline);
      com.bitmall.agent.knowledge.model.KnowledgeVectors.validate(query, snapshot.getDimension());
      if (snapshot.isDatabaseVectorSearch()) {
        runs.add(
            indexes
                .vectorSearch(
                    snapshot.getIndexVersion(), query, brand, type, properties.getCandidateLimit())
                .stream()
                .map(
                    row -> {
                      var chunk = legal.get(RowValues.string(row, "chunk_id"));
                      if (chunk == null) throw new IllegalStateException("数据库向量候选越过合法索引范围");
                      return decorate(chunk, snapshot, 0, null, List.of("VECTOR"));
                    })
                .toList());
      } else {
        // 仅显式 LEGACY 兼容模式保留旧 Java 排序，PGVECTOR 不会因故障偷偷走这里。
        runs.add(
            legal.values().stream()
                .sorted(
                    Comparator.<KnowledgeChunk>comparingDouble(c -> cosine(query, c.getVector()))
                        .reversed()
                        .thenComparing(KnowledgeChunk::getChunkId))
                .limit(properties.getCandidateLimit())
                .map(c -> decorate(c, snapshot, 0, null, List.of("VECTOR")))
                .toList());
      }
    }
    return mergeVariants(runs);
  }

  /** 变体在每个通道内只保留最佳名次，避免一个问题改写次数多就重复加权。 */
  private List<KnowledgeHitVO> mergeVariants(List<List<KnowledgeHitVO>> runs) {
    var ranks = new LinkedHashMap<String, Integer>();
    var hits = new LinkedHashMap<String, KnowledgeHitVO>();
    for (var run : runs)
      for (int i = 0; i < run.size(); i++) {
        var h = run.get(i);
        ranks.merge(h.evidenceKey(), i + 1, Math::min);
        hits.merge(h.evidenceKey(), h, (a, b) -> a.getScore() >= b.getScore() ? a : b);
      }
    return hits.values().stream()
        .sorted(
            Comparator.<KnowledgeHitVO>comparingInt(h -> ranks.get(h.evidenceKey()))
                .thenComparing(KnowledgeHitVO::evidenceKey))
        .limit(properties.getCandidateLimit())
        .toList();
  }

  /** rank 从 1 开始。关键词整数分保留原义，融合分独立返回，不能叫置信度。 */
  private List<KnowledgeHitVO> fuse(
      List<List<KnowledgeHitVO>> rankings, List<String> channels, KnowledgeIndexSnapshot snapshot) {
    var hits = new LinkedHashMap<String, KnowledgeHitVO>();
    var scores = new LinkedHashMap<String, Double>();
    var sources = new LinkedHashMap<String, List<String>>();
    for (int c = 0; c < rankings.size(); c++)
      for (int i = 0; i < rankings.get(c).size(); i++) {
        var h = rankings.get(c).get(i);
        String key = h.evidenceKey();
        hits.merge(key, h, (a, b) -> a.getScore() >= b.getScore() ? a : b);
        scores.merge(key, 1.0 / (properties.getRrfK() + i + 1), Double::sum);
        sources.computeIfAbsent(key, k -> new ArrayList<>()).add(channels.get(c));
      }
    return hits.values().stream()
        .sorted(
            Comparator.<KnowledgeHitVO>comparingDouble(h -> scores.get(h.evidenceKey()))
                .reversed()
                .thenComparing(KnowledgeHitVO::evidenceKey))
        .limit(properties.getEvidencePerGroup())
        .map(
            h ->
                h.evidence(
                    h.getChunkId(),
                    h.getIndexVersion(),
                    h.getEmbeddingModelId(),
                    h.getEmbeddingVersion(),
                    h.getDimension(),
                    h.getTextHash(),
                    scores.get(h.evidenceKey()),
                    sources.get(h.evidenceKey())))
        .toList();
  }

  private KnowledgeHitVO decorate(
      KnowledgeChunk c, KnowledgeIndexSnapshot s, int score, Double fusion, List<String> channels) {
    return KnowledgeText.withContent(c.getDocument(), c.getDocument().getContent(), score)
        .evidence(
            c.getChunkId(),
            s.getIndexVersion(),
            s.getModelId(),
            s.getModelVersion(),
            s.getDimension(),
            c.getTextHash(),
            fusion,
            channels);
  }

  public static double cosine(double[] a, double[] b) {
    double dot = 0, aa = 0, bb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      aa += a[i] * a[i];
      bb += b[i] * b[i];
    }
    return dot / Math.sqrt(aa * bb);
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
