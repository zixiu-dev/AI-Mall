package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeIndexStore;
import com.bitmall.agent.knowledge.dao.repository.KnowledgeRepository;
import com.bitmall.agent.knowledge.model.KnowledgeChunk;
import com.bitmall.agent.knowledge.model.KnowledgeIndexSnapshot;
import com.bitmall.agent.knowledge.model.KnowledgeVectors;
import com.bitmall.agent.knowledge.service.KnowledgeIndexService;
import com.bitmall.agent.knowledge.vo.KnowledgeGroupVO;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.bitmall.agent.knowledge.vo.KnowledgeIntentVO;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 知识检索执行器（只读）。
 *
 * <p>对每个检索计划并行跑两路召回：数据库关键词检索和向量检索，然后用 RRF（按名次倒数求和）把两路结果融合。
 * 两路必须使用同一个索引版本、同一组品牌/类别/地区过滤条件，否则融合出来的证据不可比。
 *
 * <p>所有任务都跑在一个有界线程池里，队列满时直接拒绝而不是无限排队，避免知识检索拖垮整个请求。
 */
@Component
public class KnowledgeRetriever {
  private static final String MODE_HYBRID = "HYBRID";

  /** 检索通道名，会出现在返回结果的 retrievalMode、retrievalChannels 和警告中。 */
  private static final String CHANNEL_KEYWORD = "DB_KEYWORD";

  private static final String CHANNEL_VECTOR = "VECTOR";

  private static final String STATUS_CLARIFICATION_REQUIRED = "CLARIFICATION_REQUIRED";
  private static final String STATUS_ERROR = "ERROR";
  private static final String STATUS_NO_MATCH = "NO_MATCH";
  private static final String STATUS_FOUND = "FOUND";

  /** 没有任何通道成功时 retrievalMode 的取值。 */
  private static final String RETRIEVAL_MODE_NONE = "NONE";

  /** 当前只收录中国大陆的政策资料。 */
  private static final String REGION_CN_MAINLAND = "CN_MAINLAND";

  /** cacheVersion 中的索引版本占位：纯关键词模式 / 混合模式下索引读取失败。 */
  private static final String CACHE_VERSION_KEYWORD_ONLY = "KEYWORD";

  private static final String CACHE_VERSION_INDEX_UNAVAILABLE = "UNAVAILABLE";

  private static final String VECTOR_INDEX_UNAVAILABLE_WARNING =
      "VECTOR_INDEX_UNAVAILABLE：索引未发布、过期或配置不兼容，本次仅运行数据库关键词检索";

  /** 单个通道失败时的警告后缀，前面拼通道名，例如 "VECTOR_FAILED：..."。 */
  private static final String BRANCH_FAILED_SUFFIX = "_FAILED：分支超时、繁忙或服务不可用";

  private static final String MESSAGE_SERVICE_UNAVAILABLE = "检索服务不可用，不能据此认定没有政策资料。";
  private static final String MESSAGE_NO_MATCH = "未收录所需品牌/类别资料，不能套用其他品牌。";

  /** 线程池上限和等待队列长度：线程数取配置值并限制在 1～8，排队最多 32 个任务。 */
  private static final int MAX_WORKER_THREADS = 8;

  private static final int WORKER_QUEUE_CAPACITY = 32;

  /** 查询政策文档原文，并在没有向量索引时直接做关键词检索。 */
  private final KnowledgeRepository documents;

  /** 已发布索引的存储，用于按索引版本做关键词检索和数据库向量检索。 */
  private final KnowledgeIndexStore indexes;

  /** 读取并校验当前已发布的索引快照（模型、维度、原文是否仍一致）。 */
  private final KnowledgeIndexService indexService;

  /** 把查询文本转换为向量。 */
  private final EmbeddingClient embedding;

  /** 读取检索模式、并发线程数、候选数、RRF 融合参数、每组证据条数及 embedding 模型版本。 */
  private final KnowledgeProperties properties;

  /** 查询增强和两路召回共用的有界线程池。 */
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
    int threadCount = Math.max(1, Math.min(MAX_WORKER_THREADS, properties.getWorkers()));
    workers =
        new ThreadPoolExecutor(
            threadCount,
            threadCount,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(WORKER_QUEUE_CAPACITY),
            runnable -> {
              Thread thread = new Thread(runnable, "knowledge-retrieval");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * 计算检索结果缓存的版本号。
   *
   * <p>请求内缓存的检索结果只有在"启用文档内容、已发布索引版本、embedding 模型配置"都没变时才能复用；
   * 任何一项变化（例如文档被停用或发布了新索引）都会得到不同的版本号，从而让旧缓存失效。
   */
  public String cacheVersion() {
    String sourceHashes =
        documents.listEnabled().stream()
            .map(KnowledgeText::documentHash)
            .collect(Collectors.joining("|"));
    String indexVersion = CACHE_VERSION_KEYWORD_ONLY;
    if (MODE_HYBRID.equals(properties.getMode())) {
      try {
        indexVersion = indexes.published(properties.getMaximumChunks()).getIndexVersion();
      } catch (RuntimeException e) {
        indexVersion = CACHE_VERSION_INDEX_UNAVAILABLE;
      }
    }
    return KnowledgeText.hash(
        String.join(
            "|",
            sourceHashes,
            indexVersion,
            properties.getEmbeddingModel(),
            properties.getEmbeddingVersion(),
            String.valueOf(properties.getEmbeddingDimension())));
  }

  /**
   * 对一批检索计划执行召回，返回与 plans 顺序一致的结果分组。
   *
   * <p>执行顺序很重要：先把所有计划的所有通道任务一次性提交到线程池，再按共同的截止时间逐个收集结果。
   * 不能"提交一个、等一个"，也不能在线程池任务里再提交子任务并阻塞等待，否则线程池会被自己占满而死锁。
   *
   * <p>无论成功与否，返回前都会取消仍未完成的任务，避免超时后的任务继续占用线程。
   *
   * @param variants 每个计划 id 对应的查询表达（原问题 + 增强改写），缺省时只用原问题
   * @param deadline System.nanoTime() 口径的截止时间
   * @throws IllegalStateException 所有非补问分组都因基础设施故障失败时
   */
  public List<KnowledgeGroupVO> retrieve(
      List<KnowledgeQueryPlanner.Plan> plans, Map<String, List<String>> variants, long deadline) {
    // 第 1 步：混合模式下读取向量索引快照；读不到就降级为只跑关键词，并给每个分组带上警告。
    KnowledgeIndexSnapshot snapshot = null;
    String indexWarning = null;
    if (needsVectorIndex(plans)) {
      try {
        snapshot = bounded(indexService::snapshot, deadline);
      } catch (RuntimeException e) {
        indexWarning = VECTOR_INDEX_UNAVAILABLE_WARNING;
      }
    }

    List<Future<?>> submitted = new ArrayList<>();
    try {
      // 第 2 步：一次性提交全部召回任务。
      Map<String, Map<String, Future<List<KnowledgeHitVO>>>> branchesByPlan =
          submitAllBranches(plans, variants, snapshot, deadline, submitted);

      // 第 3 步：按计划顺序收集结果并组装分组。
      List<KnowledgeGroupVO> groups = new ArrayList<>();
      for (KnowledgeQueryPlanner.Plan plan : plans) {
        if (plan.getIntent().isClarificationRequired()) {
          groups.add(clarificationGroup(plan));
        } else {
          groups.add(
              collectGroup(
                  plan, branchesByPlan.get(plan.getId()), variants, indexWarning, deadline));
        }
      }

      // 第 4 步：如果没有任何分组拿到有效结论，说明是基础设施故障，直接报错而不是返回"没找到"。
      failIfNoGroupSucceeded(groups);
      return groups;
    } finally {
      cancelUnfinished(submitted);
      workers.purge();
    }
  }

  /**
   * 在共享线程池中执行一个任务，并最多等到 deadline。
   *
   * <p>查询增强也用这个方法，和召回共用线程池以统一控制并发；调用方只能在召回任务提交之前使用，
   * 否则会和 retrieve 的任务抢占线程。
   */
  public <T> T bounded(Callable<T> call, long deadline) {
    Future<T> future = workers.submit(call);
    try {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new TimeoutException();
      }
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("增强已取消");
    } catch (Exception e) {
      throw new IllegalStateException("增强超时或失败", e);
    } finally {
      if (!future.isDone()) {
        future.cancel(true);
      }
      workers.purge();
    }
  }

  /** 只有混合模式、且至少有一个计划真正需要检索（不是补问）时，才去读向量索引。 */
  private boolean needsVectorIndex(List<KnowledgeQueryPlanner.Plan> plans) {
    if (!MODE_HYBRID.equals(properties.getMode())) {
      return false;
    }
    return plans.stream().anyMatch(plan -> !plan.getIntent().isClarificationRequired());
  }

  /**
   * 为每个需要检索的计划提交关键词任务；有可用索引快照时再提交向量任务。
   *
   * @param submitted 输出参数，记录所有已提交的任务，供 finally 中统一取消
   * @return 计划 id → (通道名 → 任务)，通道按"关键词、向量"的顺序排列
   */
  private Map<String, Map<String, Future<List<KnowledgeHitVO>>>> submitAllBranches(
      List<KnowledgeQueryPlanner.Plan> plans,
      Map<String, List<String>> variants,
      KnowledgeIndexSnapshot snapshot,
      long deadline,
      List<Future<?>> submitted) {
    Map<String, Map<String, Future<List<KnowledgeHitVO>>>> branchesByPlan = new LinkedHashMap<>();
    for (KnowledgeQueryPlanner.Plan plan : plans) {
      if (plan.getIntent().isClarificationRequired()) {
        continue;
      }
      List<String> queries = queriesFor(plan, variants);
      Map<String, Future<List<KnowledgeHitVO>>> branches = new LinkedHashMap<>();

      Future<List<KnowledgeHitVO>> keywordTask =
          submit(() -> keyword(plan, queries, snapshot, deadline));
      branches.put(CHANNEL_KEYWORD, keywordTask);
      submitted.add(keywordTask);

      if (snapshot != null) {
        Future<List<KnowledgeHitVO>> vectorTask =
            submit(() -> vector(plan, queries, snapshot, deadline));
        branches.put(CHANNEL_VECTOR, vectorTask);
        submitted.add(vectorTask);
      }
      branchesByPlan.put(plan.getId(), branches);
    }
    return branchesByPlan;
  }

  /** 计划的查询表达：优先用增强后的变体，没有时只用计划本身的问题。 */
  private static List<String> queriesFor(
      KnowledgeQueryPlanner.Plan plan, Map<String, List<String>> variants) {
    return variants.getOrDefault(plan.getId(), List.of(plan.getQuery()));
  }

  /** 需要用户补充信息的计划不做检索，原样返回补问提示。 */
  private static KnowledgeGroupVO clarificationGroup(KnowledgeQueryPlanner.Plan plan) {
    KnowledgeIntentVO intent = plan.getIntent();
    return new KnowledgeGroupVO(
        plan.getId(),
        plan.getQuery(),
        STATUS_CLARIFICATION_REQUIRED,
        intent.getMessage(),
        RETRIEVAL_MODE_NONE,
        intent,
        List.of(),
        new ArrayList<>(),
        List.of(plan.getQuery()),
        plan.getReferences());
  }

  /**
   * 等待一个计划的各通道结果并融合成分组。
   *
   * <p>单个通道失败只记警告，其余通道照常融合；所有通道都失败时分组状态为 ERROR，
   * 以便和"检索成功但没有匹配资料"（NO_MATCH）区分开。
   */
  private KnowledgeGroupVO collectGroup(
      KnowledgeQueryPlanner.Plan plan,
      Map<String, Future<List<KnowledgeHitVO>>> branches,
      Map<String, List<String>> variants,
      String indexWarning,
      long deadline) {
    List<String> warnings = new ArrayList<>();
    if (indexWarning != null) {
      warnings.add(indexWarning);
    }

    List<List<KnowledgeHitVO>> rankings = new ArrayList<>();
    List<String> succeededChannels = new ArrayList<>();
    for (Map.Entry<String, Future<List<KnowledgeHitVO>>> branch : branches.entrySet()) {
      String channel = branch.getKey();
      try {
        rankings.add(await(branch.getValue(), deadline));
        succeededChannels.add(channel);
      } catch (RuntimeException e) {
        warnings.add(channel + BRANCH_FAILED_SUFFIX);
      }
    }

    List<KnowledgeHitVO> hits = fuse(rankings, succeededChannels);
    boolean allChannelsFailed = rankings.isEmpty();
    String status = groupStatus(allChannelsFailed, hits);
    String retrievalMode = String.join("+", succeededChannels);
    if (retrievalMode.isEmpty()) {
      retrievalMode = RETRIEVAL_MODE_NONE;
    }
    return new KnowledgeGroupVO(
        plan.getId(),
        plan.getQuery(),
        status,
        groupMessage(status, plan.getIntent()),
        retrievalMode,
        plan.getIntent(),
        hits,
        warnings,
        queriesFor(plan, variants),
        plan.getReferences());
  }

  private static String groupStatus(boolean allChannelsFailed, List<KnowledgeHitVO> hits) {
    if (allChannelsFailed) {
      return STATUS_ERROR;
    }
    if (hits.isEmpty()) {
      return STATUS_NO_MATCH;
    }
    return STATUS_FOUND;
  }

  private static String groupMessage(String status, KnowledgeIntentVO intent) {
    if (STATUS_ERROR.equals(status)) {
      return MESSAGE_SERVICE_UNAVAILABLE;
    }
    if (STATUS_NO_MATCH.equals(status)) {
      return MESSAGE_NO_MATCH;
    }
    return intent.getMessage();
  }

  /**
   * 至少有一个分组 ERROR，且没有任何分组拿到 FOUND / NO_MATCH 时抛异常。
   *
   * <p>补问分组不算"成功"，所以"全是补问 + ERROR"也会报错；全是补问且没有 ERROR 则正常返回。
   */
  private static void failIfNoGroupSucceeded(List<KnowledgeGroupVO> groups) {
    boolean anyError = groups.stream().anyMatch(group -> STATUS_ERROR.equals(group.getStatus()));
    boolean anySucceeded =
        groups.stream()
            .anyMatch(
                group ->
                    STATUS_FOUND.equals(group.getStatus())
                        || STATUS_NO_MATCH.equals(group.getStatus()));
    if (anyError && !anySucceeded) {
      throw new IllegalStateException("知识检索基础设施不可用，请稍后重试");
    }
  }

  private static void cancelUnfinished(List<Future<?>> futures) {
    for (Future<?> future : futures) {
      if (!future.isDone()) {
        future.cancel(true);
      }
    }
  }

  /** 提交召回任务；线程池满被拒绝时不抛异常，而是返回一个已失败的 Future，由收集阶段记为该通道失败。 */
  private Future<List<KnowledgeHitVO>> submit(Callable<List<KnowledgeHitVO>> task) {
    try {
      return workers.submit(task);
    } catch (RejectedExecutionException e) {
      CompletableFuture<List<KnowledgeHitVO>> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  /**
   * 等待一个召回任务，最多等到 deadline；失败或超时都会取消该任务并抛 IllegalStateException。
   *
   * <p>已过截止时间但任务恰好已完成时，仍然取它的结果，不浪费已经算完的工作。
   */
  private List<KnowledgeHitVO> await(Future<List<KnowledgeHitVO>> future, long deadline) {
    try {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0 && !future.isDone()) {
        throw new TimeoutException();
      }
      return future.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new IllegalStateException("检索已取消");
    } catch (Exception e) {
      future.cancel(true);
      throw new IllegalStateException("检索分支失败", e);
    }
  }

  /** 在任务内部的每个耗时步骤前调用：线程已被取消或已过截止时间就立即停止。 */
  private void ensureBeforeDeadline(long deadline) {
    boolean cancelled = Thread.currentThread().isInterrupted();
    boolean expired = System.nanoTime() >= deadline;
    if (cancelled || expired) {
      throw new IllegalStateException("检索截止时间已到");
    }
  }

  /**
   * 关键词通道：对每个查询表达各检索一次，再合并成一个排名。
   *
   * <p>没有索引快照时直接查文档表；有快照时查同一索引版本里的知识块，保证和向量通道的结果可以融合。
   */
  private List<KnowledgeHitVO> keyword(
      KnowledgeQueryPlanner.Plan plan,
      List<String> queries,
      KnowledgeIndexSnapshot snapshot,
      long deadline) {
    String brand = plan.getIntent().getBrands().get(0);
    String type = plan.getIntent().getCategories().get(0);
    List<List<KnowledgeHitVO>> runs = new ArrayList<>();
    for (String query : queries) {
      ensureBeforeDeadline(deadline);
      if (snapshot == null) {
        runs.add(documents.search(query, brand, type, properties.getCandidateLimit()));
      } else {
        runs.add(keywordSearchInIndex(snapshot, query, brand, type));
      }
    }
    return mergeVariants(runs);
  }

  /** 在已发布索引内做关键词检索；返回的块必须属于当前快照，否则说明索引数据不一致。 */
  private List<KnowledgeHitVO> keywordSearchInIndex(
      KnowledgeIndexSnapshot snapshot, String query, String brand, String type) {
    Map<String, KnowledgeChunk> chunksById =
        snapshot.getChunks().stream()
            .collect(Collectors.toMap(KnowledgeChunk::getChunkId, chunk -> chunk));
    List<Map<String, Object>> rows =
        indexes.search(
            snapshot.getIndexVersion(), query, brand, type, properties.getCandidateLimit());
    List<KnowledgeHitVO> hits = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      KnowledgeChunk chunk = chunksById.get(RowValues.string(row, "chunk_id"));
      if (chunk == null) {
        throw new IllegalStateException("关键词候选不属于当前索引");
      }
      hits.add(decorate(chunk, snapshot, RowValues.intValue(row, "score"), CHANNEL_KEYWORD));
    }
    return List.copyOf(hits);
  }

  /**
   * 向量通道：先按品牌、类别、地区圈定合法知识块，再对每个查询表达做向量检索并合并排名。
   *
   * <p>合法范围为空时直接返回空结果，不调用 embedding 模型。
   */
  private List<KnowledgeHitVO> vector(
      KnowledgeQueryPlanner.Plan plan,
      List<String> queries,
      KnowledgeIndexSnapshot snapshot,
      long deadline) {
    ensureBeforeDeadline(deadline);
    String brand = plan.getIntent().getBrands().get(0);
    String type = plan.getIntent().getCategories().get(0);
    Map<String, KnowledgeChunk> allowedChunks = allowedChunks(snapshot, brand, type);
    if (allowedChunks.isEmpty()) {
      return List.of();
    }

    List<double[]> queryVectors = embedding.embed(queries, deadline);
    if (queryVectors.size() != queries.size()) {
      throw new IllegalStateException("查询向量数量不符");
    }

    List<List<KnowledgeHitVO>> runs = new ArrayList<>();
    for (double[] queryVector : queryVectors) {
      ensureBeforeDeadline(deadline);
      KnowledgeVectors.validate(queryVector, snapshot.getDimension());
      if (snapshot.isDatabaseVectorSearch()) {
        runs.add(vectorSearchInDatabase(snapshot, queryVector, brand, type, allowedChunks));
      } else {
        runs.add(vectorSearchInMemory(snapshot, queryVector, allowedChunks));
      }
    }
    return mergeVariants(runs);
  }

  /** 快照中品牌、类别一致且地区为中国大陆的知识块，按 chunkId 索引。 */
  private static Map<String, KnowledgeChunk> allowedChunks(
      KnowledgeIndexSnapshot snapshot, String brand, String type) {
    return snapshot.getChunks().stream()
        .filter(
            chunk -> {
              KnowledgeHitVO document = chunk.getDocument();
              return brand.equals(document.getBrand())
                  && type.equals(document.getDocumentType())
                  && REGION_CN_MAINLAND.equals(document.getRegion());
            })
        .collect(Collectors.toMap(KnowledgeChunk::getChunkId, chunk -> chunk));
  }

  /** 由数据库（pgvector）执行向量检索；返回的块必须落在合法范围内，否则视为过滤失效直接报错。 */
  private List<KnowledgeHitVO> vectorSearchInDatabase(
      KnowledgeIndexSnapshot snapshot,
      double[] queryVector,
      String brand,
      String type,
      Map<String, KnowledgeChunk> allowedChunks) {
    List<Map<String, Object>> rows =
        indexes.vectorSearch(
            snapshot.getIndexVersion(), queryVector, brand, type, properties.getCandidateLimit());
    List<KnowledgeHitVO> hits = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      KnowledgeChunk chunk = allowedChunks.get(RowValues.string(row, "chunk_id"));
      if (chunk == null) {
        throw new IllegalStateException("数据库向量候选越过合法索引范围");
      }
      hits.add(decorate(chunk, snapshot, 0, CHANNEL_VECTOR));
    }
    return List.copyOf(hits);
  }

  /**
   * 在 Java 内存中按余弦相似度排序（相似度相同按 chunkId 排）。
   *
   * <p>只有显式配置的 LEGACY 兼容存储会走这里；PGVECTOR 出故障时不会悄悄退回到这条路径。
   */
  private List<KnowledgeHitVO> vectorSearchInMemory(
      KnowledgeIndexSnapshot snapshot,
      double[] queryVector,
      Map<String, KnowledgeChunk> allowedChunks) {
    Comparator<KnowledgeChunk> mostSimilarFirst =
        Comparator.<KnowledgeChunk>comparingDouble(
                chunk -> cosine(queryVector, chunk.getVector()))
            .reversed()
            .thenComparing(KnowledgeChunk::getChunkId);
    return allowedChunks.values().stream()
        .sorted(mostSimilarFirst)
        .limit(properties.getCandidateLimit())
        .map(chunk -> decorate(chunk, snapshot, 0, CHANNEL_VECTOR))
        .toList();
  }

  /**
   * 合并同一通道内多个查询表达的检索结果。
   *
   * <p>同一证据在多个表达中出现时只取它的最好名次，不累加；否则问题被改写的次数越多，
   * 某条证据的权重就越高。分数相同的重复证据保留先出现的那条。
   */
  private List<KnowledgeHitVO> mergeVariants(List<List<KnowledgeHitVO>> runs) {
    Map<String, Integer> bestRanks = new LinkedHashMap<>();
    Map<String, KnowledgeHitVO> hitsByKey = new LinkedHashMap<>();
    for (List<KnowledgeHitVO> run : runs) {
      for (int index = 0; index < run.size(); index++) {
        KnowledgeHitVO hit = run.get(index);
        int rank = index + 1;
        bestRanks.merge(hit.evidenceKey(), rank, Math::min);
        hitsByKey.merge(hit.evidenceKey(), hit, KnowledgeRetriever::higherScore);
      }
    }
    Comparator<KnowledgeHitVO> bestRankFirst =
        Comparator.<KnowledgeHitVO>comparingInt(hit -> bestRanks.get(hit.evidenceKey()))
            .thenComparing(KnowledgeHitVO::evidenceKey);
    return hitsByKey.values().stream()
        .sorted(bestRankFirst)
        .limit(properties.getCandidateLimit())
        .toList();
  }

  /**
   * 用 RRF 融合各通道排名：每条证据的融合分 = Σ 1 / (rrfK + 名次)，名次从 1 开始。
   *
   * <p>融合分单独放在 fusionScore 字段返回；原来的整数 score 仍只表示关键词分数，两者都不是置信度。
   *
   * @param channels 与 rankings 一一对应的通道名，用于记录每条证据来自哪些通道
   */
  private List<KnowledgeHitVO> fuse(List<List<KnowledgeHitVO>> rankings, List<String> channels) {
    Map<String, KnowledgeHitVO> hitsByKey = new LinkedHashMap<>();
    Map<String, Double> fusionScores = new LinkedHashMap<>();
    Map<String, List<String>> sourceChannels = new LinkedHashMap<>();
    for (int channelIndex = 0; channelIndex < rankings.size(); channelIndex++) {
      List<KnowledgeHitVO> ranking = rankings.get(channelIndex);
      String channel = channels.get(channelIndex);
      for (int index = 0; index < ranking.size(); index++) {
        KnowledgeHitVO hit = ranking.get(index);
        String key = hit.evidenceKey();
        int rank = index + 1;
        hitsByKey.merge(key, hit, KnowledgeRetriever::higherScore);
        fusionScores.merge(key, 1.0 / (properties.getRrfK() + rank), Double::sum);
        sourceChannels.computeIfAbsent(key, ignored -> new ArrayList<>()).add(channel);
      }
    }
    Comparator<KnowledgeHitVO> highestFusionFirst =
        Comparator.<KnowledgeHitVO>comparingDouble(hit -> fusionScores.get(hit.evidenceKey()))
            .reversed()
            .thenComparing(KnowledgeHitVO::evidenceKey);
    return hitsByKey.values().stream()
        .sorted(highestFusionFirst)
        .limit(properties.getEvidencePerGroup())
        .map(
            hit ->
                hit.evidence(
                    hit.getChunkId(),
                    hit.getIndexVersion(),
                    hit.getEmbeddingModelId(),
                    hit.getEmbeddingVersion(),
                    hit.getDimension(),
                    hit.getTextHash(),
                    fusionScores.get(hit.evidenceKey()),
                    sourceChannels.get(hit.evidenceKey())))
        .toList();
  }

  /** 同一证据重复出现时保留分数更高的那条；分数相同保留先出现的。 */
  private static KnowledgeHitVO higherScore(KnowledgeHitVO existing, KnowledgeHitVO incoming) {
    return existing.getScore() >= incoming.getScore() ? existing : incoming;
  }

  /** 把知识块转换成带索引版本、模型信息和来源通道的证据对象（融合分此时还未计算，为 null）。 */
  private KnowledgeHitVO decorate(
      KnowledgeChunk chunk, KnowledgeIndexSnapshot snapshot, int keywordScore, String channel) {
    return KnowledgeText.withContent(
            chunk.getDocument(), chunk.getDocument().getContent(), keywordScore)
        .evidence(
            chunk.getChunkId(),
            snapshot.getIndexVersion(),
            snapshot.getModelId(),
            snapshot.getModelVersion(),
            snapshot.getDimension(),
            chunk.getTextHash(),
            null,
            List.of(channel));
  }

  public static double cosine(double[] a, double[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    return dot / Math.sqrt(normA * normB);
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
