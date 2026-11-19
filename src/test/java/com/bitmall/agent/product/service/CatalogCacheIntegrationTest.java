package com.bitmall.agent.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.product.cache.CacheMetrics;
import com.bitmall.agent.product.cache.CatalogCache;
import com.bitmall.agent.product.cache.HotKeyDetector;
import com.bitmall.agent.product.cache.RedisCatalogStore;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 使用独立临时 Redis 验证商品缓存。 覆盖冷热切换、空值缓存、并发回源、旧数据回填拦截、提交后失效和 Redis 故障降级。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "app.catalog-cache.enabled=true",
      "app.hotkey.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:cache-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("demo")
class CatalogCacheIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  com.bitmall.agent.order.service.OrderTraceService orderTraces;

  /** 本次缓存测试单独启动的 Redis 子进程，测试结束后会停止。 */
  private static Process server;

  /** 本次 Redis 使用的随机本机端口，避免占用开发者默认端口。 */
  private static int port;

  /** 本轮随机缓存前缀，隔离不同测试的数据。 */
  private static final String namespace = "wowo:test:" + UUID.randomUUID();

  /** 启动独立临时 Redis，使用随机端口和目录，避免影响开发者已有 Redis 数据。 */
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var dir = Files.createTempDirectory("wowo-redis-test-");
    server =
        new ProcessBuilder(
                System.getenv().getOrDefault("REDIS_SERVER", "redis-server"),
                "--bind",
                "127.0.0.1",
                "--port",
                Integer.toString(port),
                "--save",
                "",
                "--appendonly",
                "no",
                "--dir",
                dir.toString())
            .redirectErrorStream(true)
            .redirectOutput(dir.resolve("redis.log").toFile())
            .start();
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (true) {
      try (Socket ignored = new Socket("127.0.0.1", port)) {
        break;
      } catch (Exception ex) {
        if (System.nanoTime() > deadline) throw ex;
        Thread.sleep(20);
      }
    }
    registry.add("spring.data.redis.port", () -> port);
    registry.add("app.catalog-cache.namespace", () -> namespace);
  }

  /** 测试结束后停止本次创建的 Redis 子进程。 */
  @AfterAll
  static void stopRedis() {
    if (server != null) server.destroy();
  }

  /** 缓存集成测试专用配置。 用 TestHotKeys 替代京东热点客户端，让测试可以主动切换热点状态。 */
  @TestConfiguration
  static class Config {
    /** 提供可控制的热点探测器，替代真实京东客户端。 */
    @Bean
    @Primary
    TestHotKeys testHotKeys() {
      return new TestHotKeys();
    }
  }

  /** 测试用热点探测器。 通过一个原子布尔值决定所有查询是否视为热点，不启动真实 HotKey 线程。 */
  static class TestHotKeys implements HotKeyDetector {
    /** 测试可主动切换的热点标志，替代真实 Worker 的异步标记。 */
    final AtomicBoolean hot = new AtomicBoolean();

    /** 返回测试开关，决定这次查询是否视为热点。 */
    public boolean isHot(String key) {
      return hot.get();
    }

    /** 返回测试探测器标识，供缓存状态接口使用。 */
    public Map<String, Object> status() {
      return Map.of("testDetector", true);
    }
  }

  /** 依赖组件：商品展示查询的业务入口。 */
  @Autowired ProductService catalog;

  /** 依赖组件：统一计算立即购买和购物车的结算报价。 */
  @Autowired CheckoutService checkout;

  /** 依赖组件：订单交易业务服务。 */
  @Autowired OrderService orders;

  /** 依赖组件：商品公共查询的多级缓存入口。 */
  @Autowired CatalogCache cache;

  @Autowired com.bitmall.agent.product.cache.CatalogBloomFilter bloom;
  @Autowired com.bitmall.agent.product.config.CatalogProtectionProperties protection;
  @Autowired com.bitmall.agent.product.cache.CacheTtlPolicy ttls;

  /** 依赖组件：记录缓存运行计数，例如本机命中、Redis 命中、数据库回源和失败次数。 */
  @Autowired CacheMetrics metrics;

  /** 依赖组件：负责商品缓存与 Redis 的交互。 */
  @Autowired RedisCatalogStore store;

  /** Redis 字符串访问工具，用于脚本执行、缓存读写和发布失效消息。 */
  @Autowired StringRedisTemplate redis;

  /** 测试热点探测器，允许测试立即切换冷热状态。 */
  @Autowired TestHotKeys hot;

  /** 执行带绑定参数的 SQL 查询和更新；只在 Mapper 中使用。 */
  @Autowired JdbcClient jdbc;

  /** Spring 事务管理器，测试用它明确构造提交或回滚场景。 */
  @Autowired PlatformTransactionManager transactions;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  @Autowired ObjectMapper json;

  /** 依赖组件：读取 app.catalog-cache 配置。 */
  @Autowired CatalogCacheProperties properties;

  /** 在每个测试之前清理控制状态，避免前一个测试影响本次断言。 */
  @BeforeEach
  void reset() throws Exception {
    bloom.rebuild();
    long readyUntil = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!"1".equals(redis.opsForHash().get(bloom.metadataKey(), "ready"))) {
      if (System.nanoTime() > readyUntil) throw new IllegalStateException("Bloom did not warm up");
      Thread.sleep(10);
    }
    hot.hot.set(false);
    store.client().getKeys().deleteByPattern(namespace + ":{catalog}:display:*");
    cache.invalidateLocal();
    // Drain the asynchronous notification before measuring a cache hit.
    Thread.sleep(80);
  }

  /** 读取某项缓存指标，比较测试前后的增量。 */
  long count(String name) {
    return metrics.snapshot().getOrDefault(name, 0L);
  }

  /** 首次商品查询回源数据库，再次通过 Redis 读取，并保留结构化属性。 */
  @Test
  void coldReadLoadsDatabaseThenOrdinaryReadUsesRedisWithTypedAttributes() {
    long before = count("database_load");
    var first = catalog.product(108);
    var second = catalog.product(108);
    assertThat(second).isEqualTo(first);
    assertThat(second.getAttributes()).containsEntry("material", "网布");
    assertThat(count("database_load") - before).isEqualTo(1);
    assertThat(count("redis_hit")).isPositive();
  }

  /** 热点时使用本机缓存，变冷后停止本机命中并改走 Redis。 */
  @Test
  void hotReadUsesCaffeineAndStopsWhenKeyCools() {
    hot.hot.set(true);
    catalog.product(108);
    long before = count("local_hit"), bloomChecks = count("bloom_bypassed");
    store.client().getBucket(bloom.metadataKey()).delete();
    catalog.product(108);
    assertThat(count("local_hit") - before).isEqualTo(1);
    assertThat(count("bloom_bypassed")).isEqualTo(bloomChecks);
    hot.hot.set(false);
    long redisBefore = count("redis_hit");
    catalog.product(108);
    assertThat(count("redis_hit") - redisBefore).isEqualTo(1);
  }

  /** 不存在的商品只查询数据库一次，后续短期内命中空结果缓存。 */
  @Test
  void missingProductIsNegativelyCachedInsteadOfRepeatedDatabaseLoads() {
    // 模拟布隆误判：发布前登记后事务回滚，位不能删除，空值缓存继续兜底。
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              bloom.registerBeforePublish(999999);
              tx.setRollbackOnly();
            });
    long before = count("database_load");
    assertThatThrownBy(() -> catalog.product(999999)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> catalog.product(999999)).isInstanceOf(IllegalArgumentException.class);
    assertThat(count("database_load") - before).isEqualTo(1);
  }

  /** 相同筛选条件复用缓存，改变页码必须使用另一份结果。 */
  @Test
  void searchCacheIncludesAllFiltersAndPages() {
    var r =
        new ProductSearchRequest(
            "跑鞋",
            null,
            null,
            null,
            new java.math.BigDecimal("500"),
            "黑色",
            "42",
            null,
            null,
            true,
            "priceAsc",
            1,
            2);
    long before = count("database_load");
    var first = catalog.searchCatalog(r);
    assertThat(catalog.searchCatalog(r)).isEqualTo(first);
    assertThat(count("database_load") - before).isEqualTo(1);
    var next =
        new ProductSearchRequest(
            r.getQuery(),
            null,
            null,
            null,
            r.getMaxPrice(),
            r.getColor(),
            r.getSize(),
            null,
            null,
            true,
            r.getSort(),
            2,
            2);
    assertThat(catalog.searchCatalog(next).getPage()).isEqualTo(2);
    assertThat(count("database_load") - before).isEqualTo(2);
  }

  /** 缓存内容损坏时删除坏值，并用真实数据库结果重建。 */
  @Test
  void corruptedRedisValueIsDiscardedAndRebuilt() {
    var found = store.lookup("product:108");
    assertThat(store.fill("product:108", found.getTicket(), "broken-json", Duration.ofSeconds(10)))
        .isTrue();
    long before = count("corrupt_entry");
    assertThat(catalog.product(108).getId()).isEqualTo(108);
    assertThat(count("corrupt_entry") - before).isEqualTo(1);
  }

  /** 多个并发请求查询同一个空缓存键时，只允许一次实际数据库加载。 */
  @Test
  void identicalConcurrentMissesCoalesceIntoOneDatabaseLoad() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        tasks.add(
            () ->
                cache.get(
                    "same-miss",
                    String.class,
                    Duration.ofSeconds(5),
                    () -> {
                      calls.incrementAndGet();
                      try {
                        Thread.sleep(60);
                      } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                      }
                      return "fresh";
                    }));
      for (var result : pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo("fresh");
      assertThat(calls.get()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  /** 旧查询进行中删除同一键，验证旧结果不能回填污染新缓存。 */
  @Test
  void oldLoaderCannotFillCacheAfterKeyInvalidation() throws Exception {
    CountDownLatch reading = new CountDownLatch(1), finish = new CountDownLatch(1);
    var pool = Executors.newSingleThreadExecutor();
    try {
      var result =
          pool.submit(
              () ->
                  cache.get(
                      "racing-miss",
                      String.class,
                      Duration.ofSeconds(5),
                      () -> {
                        reading.countDown();
                        try {
                          if (!finish.await(3, TimeUnit.SECONDS))
                            throw new IllegalStateException("test timeout");
                        } catch (InterruptedException ex) {
                          throw new RuntimeException(ex);
                        }
                        return "old";
                      }));
      assertThat(reading.await(3, TimeUnit.SECONDS)).isTrue();
      store.invalidate(List.of("racing-miss"), true);
      finish.countDown();
      assertThat(result.get(3, TimeUnit.SECONDS))
          .isEqualTo("old"); // In-flight response may finish.
      assertThat(store.lookup("racing-miss").getValue())
          .isNull(); // It cannot poison subsequent reads.
      assertThat(cache.get("racing-miss", String.class, Duration.ofSeconds(5), () -> "new"))
          .isEqualTo("new");
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
  }

  /** 事务回滚不触发提交后失效，成功建单与取消才更新可见库存。 */
  @Test
  void orderAndCancellationInvalidateStockOnlyAfterCommitAndRollbackDoesNotPublish()
      throws Exception {
    var original = catalog.product(101);
    int stock =
        original.getSkus().stream()
            .filter(s -> s.getId() == 1001)
            .findFirst()
            .orElseThrow()
            .getStock();
    var quote = checkout.checkoutQuote(10001, 1001, 1, null);
    var command = new CreateOrderCommand("DIRECT", 1001L, 1, null, null, quote.getPayAmount());
    catalog.product(108);
    String profile = store.lookup("product:101").getValue();
    assertThat(profile).doesNotContain("\"price\"", "\"stock\"", "originalPrice");
    String other = store.lookup("product:108").getValue();
    String price = store.lookup("sku:price:1001").getValue();
    long before = count("database_load");
    new TransactionTemplate(transactions)
        .execute(
            status -> {
              orders.createOrder(10001, orderTraces.prepare(10001, command, null).getTraceId());
              status.setRollbackOnly();
              return null;
            });
    assertThat(store.lookup("sku:availability:1001").getValue()).isEqualTo(String.valueOf(stock));
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1001").query(Integer.class).single())
        .isEqualTo(stock);
    var order = orders.createOrder(10001, orderTraces.prepare(10001, command, null).getTraceId());
    try {
      assertThat(store.lookup("sku:availability:1001").getValue()).isNull();
      assertThat(store.lookup("product:101").getValue()).isEqualTo(profile);
      assertThat(store.lookup("product:108").getValue()).isEqualTo(other);
      assertThat(store.lookup("sku:price:1001").getValue()).isEqualTo(price);
      catalog.product(108);
      assertThat(count("database_load")).isEqualTo(before);
      assertThat(
              catalog.product(101).getSkus().stream()
                  .filter(s -> s.getId() == 1001)
                  .findFirst()
                  .orElseThrow()
                  .getStock())
          .isEqualTo(stock - 1);
    } finally {
      orders.cancelOrder(10001, order.get("orderId").toString());
    }
    assertThat(
            catalog.product(101).getSkus().stream()
                .filter(s -> s.getId() == 1001)
                .findFirst()
                .orElseThrow()
                .getStock())
        .isEqualTo(stock);
  }

  /** 慢 SQL 读取后发生建单：同批中的价格可以填，已经失效的库存不能被旧查询复活。 */
  @Test
  void staleOfferBatchCannotRestoreInvalidatedStock() {
    var names = List.of("sku:price:1001", "sku:availability:1001");
    assertThat(store.acquireLoad("offers:101")).isTrue();
    String owner = store.ownerToken();
    try {
      var read = store.lookupMany(names);
      store.invalidate(List.of(names.get(1)), false);
      store.fillOffers(
          "offers:101",
          owner,
          read,
          Map.of(names.get(0), "999/1000", names.get(1), "10"),
          key -> 3000L);
      var after = store.lookupMany(names);
      assertThat(after.get(names.get(0)).getValue()).isEqualTo("999/1000");
      assertThat(after.get(names.get(1)).getValue()).isNull();
    } finally {
      store.releaseLoad("offers:101", owner);
    }
  }

  /** 连续购买一百次；每次读取两款商品并检查库存下降，两款资料都不应再次回源。 */
  @Test
  void hundredOrdersKeepBothProfilesWarm() {
    int previous =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1001").query(Integer.class).single();
    jdbc.sql("UPDATE product_sku SET stock=120 WHERE id=1001").update();
    var created = new ArrayList<String>();
    try {
      catalog.product(101);
      catalog.product(108);
      long loads = count("database_load");
      var quote = checkout.checkoutQuote(10001, 1001, 1, null);
      var command = new CreateOrderCommand("DIRECT", 1001L, 1, null, null, quote.getPayAmount());
      for (int n = 1; n <= 100; n++) {
        var order =
            orders.createOrder(10001, orderTraces.prepare(10001, command, null).getTraceId());
        created.add(order.get("orderId").toString());
        assertThat(
                catalog.product(101).getSkus().stream()
                    .filter(sku -> sku.getId() == 1001)
                    .findFirst()
                    .orElseThrow()
                    .getStock())
            .isEqualTo(120 - n);
        assertThat(catalog.product(108).getId()).isEqualTo(108);
      }
      assertThat(count("database_load")).isEqualTo(loads);
    } finally {
      for (String id : created) orders.cancelOrder(10001, id);
      jdbc.sql("UPDATE product_sku SET stock=:stock WHERE id=1001")
          .param("stock", previous)
          .update();
    }
  }

  /** 故意污染商品展示缓存，验证结算仍读取数据库价格和库存。 */
  @Test
  void checkoutReadsRealDatabaseEvenWhenCachedDetailIsPoisoned() {
    var expected =
        jdbc.sql("SELECT price FROM product_sku WHERE id=1001")
            .query(java.math.BigDecimal.class)
            .single();
    var found = store.lookup("product:101");
    store.fill("product:101", found.getTicket(), "{\"product\":null}", Duration.ofSeconds(10));
    assertThatThrownBy(() -> catalog.product(101)).isInstanceOf(IllegalArgumentException.class);
    assertThat(checkout.checkoutQuote(10001, 1001, 1, null).getGoodsAmount()).isEqualTo(expected);
  }

  /** 收到其他实例的失效消息后，本机已经预热的数据必须清空。 */
  @Test
  void pubsubInvalidatesAlreadyWarmedLocalCache() throws Exception {
    // 订阅器故意后台启动，PUBLISH 不保存历史消息；先验证订阅就绪，不能依赖应用启动耗时。
    long readyBefore = count("local_invalidation");
    long readyUntil = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < readyUntil) {
      Long listeners = redis.convertAndSend(store.channel(), "subscription-readiness-probe");
      if (listeners != null && listeners > 0) break;
      Thread.sleep(20);
    }
    while (count("local_invalidation") == readyBefore && System.nanoTime() < readyUntil)
      Thread.sleep(20);
    assertThat(count("local_invalidation"))
        .as("Redis subscription must be ready before publishing the test event")
        .isGreaterThan(readyBefore);
    hot.hot.set(true);
    catalog.product(108);
    catalog.product(108);
    long before = count("local_invalidation");
    redis.convertAndSend(store.channel(), "product:108");
    long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (count("local_invalidation") == before && System.nanoTime() < until) Thread.sleep(20);
    assertThat(count("local_invalidation")).isGreaterThan(before);
    long localBefore = count("local_hit");
    catalog.product(108);
    assertThat(count("local_hit")).isEqualTo(localBefore);
  }

  /** 模拟 Redis 故障，验证短暂熔断且数据库查询仍可返回。 */
  @Test
  void redisFailureOpensCircuitAndDatabaseStillAnswers() {
    @SuppressWarnings("unchecked")
    var broken =
        (org.springframework.beans.factory.ObjectProvider<org.redisson.api.RedissonClient>)
            mock(org.springframework.beans.factory.ObjectProvider.class);
    when(broken.getObject()).thenThrow(new IllegalStateException("模拟 Redisson 不可用"));
    var stats = new CacheMetrics(new SimpleMeterRegistry());
    var down = new RedisCatalogStore(broken, properties, stats);
    var isolated = new CatalogCache(properties, down, hot, json, stats);
    assertThat(isolated.get("outage", String.class, Duration.ofSeconds(5), () -> "from-db"))
        .isEqualTo("from-db");
    assertThat(isolated.get("outage", String.class, Duration.ofSeconds(5), () -> "from-db"))
        .isEqualTo("from-db");
    assertThat(down.available()).isFalse();
    verify(broken, times(1)).getObject();
    assertThat(stats.snapshot().get("database_load")).isEqualTo(2);
  }

  /** 随机非法 ID 被位图拦截，不能每次穿透到数据库，也不制造海量空键。 */
  @Test
  void bloomRejectsMissingIdsWithoutDatabaseLoads() {
    // Bloom 允许误判，不能假设固定 100 个 ID 最多只有一个误判。
    // 先确定 100 个本次过滤明确否定的 ID，再验证它们全部不回源、不申请重建锁。
    var absent = new java.util.ArrayList<Long>();
    for (long id = 7_000_000; id < 7_010_000 && absent.size() < 100; id++)
      if (!bloom.mightContain(id)) absent.add(id);
    assertThat(absent).hasSize(100);
    long before = count("database_load"),
        rejected = count("bloom_rejected"),
        locks = count("rebuild_requested");
    for (long missing : absent)
      assertThatThrownBy(() -> catalog.product(missing))
          .isInstanceOf(IllegalArgumentException.class);
    assertThat(count("database_load") - before).isZero();
    assertThat(count("bloom_rejected") - rejected).isEqualTo(100);
    assertThat(count("rebuild_requested") - locks).isZero();
  }

  /** 两个独立缓存实例没有共享 Java 锁，仍应只让一个实例回源。 */
  @Test
  void distributedDoubleCheckCombinesLoadsAcrossTwoInstances() throws Exception {
    var other =
        org.redisson.Redisson.create(new org.redisson.config.Config(store.client().getConfig()));
    var secondStore = storeFor(other, metrics);
    var second =
        new CatalogCache(properties, secondStore, hot, json, metrics, bloom, protection, ttls);
    var calls = new AtomicInteger();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(12);
    var tasks = new ArrayList<Future<String>>();
    try {
      for (int i = 0; i < 12; i++) {
        var instance = i % 2 == 0 ? cache : second;
        tasks.add(
            executor.submit(
                () -> {
                  start.await();
                  return instance.get(
                      "shared-rebuild",
                      String.class,
                      Duration.ofSeconds(10),
                      () -> {
                        calls.incrementAndGet();
                        try {
                          Thread.sleep(100);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return "same-value";
                      });
                }));
      }
      start.countDown();
      for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isEqualTo("same-value");
      assertThat(calls.get()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      other.shutdown();
    }
  }

  /** 两个独立 Redisson 客户端模拟两个 JVM；旧持有者失锁后不能回填或解开新持有者的锁。 */
  @Test
  void expiredOwnerCannotUnlockOrFillAfterAnotherOwnerAcquiresLease() {
    var other =
        org.redisson.Redisson.create(new org.redisson.config.Config(store.client().getConfig()));
    var second = storeFor(other, metrics);
    var found = store.lookup("owner-test");
    try {
      assertThat(store.acquireLoad("owner-test")).isTrue();
      String old = store.ownerToken();
      store.client().getLock(store.lockKey("owner-test")).forceUnlock(); // 模拟租约丢失
      assertThat(second.acquireLoad("owner-test")).isTrue();
      String next = second.ownerToken();
      store.releaseLoad("owner-test", old);
      assertThat(store.acquireLoad("owner-test")).isFalse();
      assertThat(
              store.fillOwned(
                  "owner-test", found.getTicket(), "obsolete", Duration.ofSeconds(2), old))
          .isFalse();
      assertThat(
              second.fillOwned(
                  "owner-test", found.getTicket(), "current", Duration.ofSeconds(2), next))
          .isTrue();
      second.releaseLoad("owner-test", next);
    } finally {
      other.shutdown();
    }
  }

  /** 看门狗能跨过初始短周期续期，不用把固定租约当成最长数据库耗时。 */
  @Test
  void redissonWatchdogKeepsLiveRebuilderOwned() throws Exception {
    var other =
        org.redisson.Redisson.create(
            new org.redisson.config.Config(store.client().getConfig()).setLockWatchdogTimeout(600));
    var second = storeFor(other, metrics);
    try {
      assertThat(second.acquireLoad("watchdog-test")).isTrue();
      String owner = second.ownerToken();
      Thread.sleep(1600);
      assertThat(store.acquireLoad("watchdog-test")).isFalse();
      second.releaseLoad("watchdog-test", owner);
      assertThat(store.acquireLoad("watchdog-test")).isTrue();
      store.releaseLoad("watchdog-test", store.ownerToken());
    } finally {
      other.shutdown();
    }
  }

  private RedisCatalogStore storeFor(org.redisson.api.RedissonClient client, CacheMetrics stats) {
    var factory =
        new org.springframework.beans.factory.support.StaticListableBeanFactory(
            java.util.Map.of("client", client));
    return new RedisCatalogStore(
        factory.getBeanProvider(org.redisson.api.RedissonClient.class), properties, stats);
  }

  @Test
  void ttlRandomDelayUsesConfiguredRatioAndDoesNotExpireAllKeysTogether() {
    var ttlsSeen = new HashSet<Long>();
    for (int i = 0; i < 12; i++) {
      String key = "ttl-probe-" + i;
      cache.get(key, String.class, Duration.ofSeconds(10), () -> "value");
      Long remaining = redis.getExpire(store.key(key), TimeUnit.MILLISECONDS);
      assertThat(remaining).isBetween(9700L, 12000L);
      ttlsSeen.add(remaining);
    }
    assertThat(ttlsSeen.size()).isGreaterThan(4);
  }

  @Test
  void missingBloomBitmapFailsOpenAndCanBeRebuilt() {
    redis.delete(bloom.filterKey());
    long before = count("database_load");
    try {
      assertThat(catalog.product(108).getId()).isEqualTo(108);
      assertThat(count("database_load") - before).isEqualTo(1);
    } finally {
      assertThat(bloom.rebuild()).isTrue();
    }
  }

  /** 扫描只看得到已提交行，但发布前登记的位也必须跨重建保留。 */
  @Test
  void rebuildRetainsProductRegisteredBeforeDatabaseCommit() throws Exception {
    long id = 8_000_001;
    var executor = Executors.newSingleThreadExecutor();
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              tx -> {
                bloom.registerBeforePublish(id);
                try {
                  assertThat(executor.submit(bloom::rebuild).get(5, TimeUnit.SECONDS)).isTrue();
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
                jdbc.sql(
                        "INSERT INTO"
                            + " product(id,name,category,brand,summary,image_key,rating,sales_count,enabled)"
                            + " SELECT"
                            + " :id,name,category,brand,summary,image_key,rating,sales_count,TRUE"
                            + " FROM product WHERE id=108")
                    .param("id", id)
                    .update();
              });
      assertThat(bloom.mightContain(id)).isTrue();
      assertThat(catalog.product(id).getId()).isEqualTo(id);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void registrationAfterBitmapLossDoesNotMakePartialBitmapReady() {
    redis.delete(bloom.filterKey());
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              bloom.registerBeforePublish(9_000_001);
              tx.setRollbackOnly();
            });
    assertThat(redis.opsForHash().get(bloom.metadataKey(), "ready")).isEqualTo("0");
    assertThat(bloom.mightContain(108)).isTrue();
    assertThat(bloom.rebuild()).isTrue();
    assertThat(bloom.mightContain(108)).isTrue();
  }

  /** 两个独立客户端使用相同原生 Bloom 数据和编码，跨实例登记可见。 */
  @Test
  void nativeBloomIsSharedAcrossIndependentRedissonClients() {
    var other =
        org.redisson.Redisson.create(new org.redisson.config.Config(store.client().getConfig()));
    try {
      var nativeBloom =
          other.<Long>getBloomFilter(
              bloom.filterKey(), org.redisson.client.codec.LongCodec.INSTANCE);
      assertThat(nativeBloom.contains(108L)).isTrue();
      nativeBloom.add(9_100_001L);
      assertThat(bloom.mightContain(9_100_001L)).isTrue();
    } finally {
      other.shutdown();
    }
  }

  /** 参数错误不能触发存在性判断或任何缓存重建竞争。 */
  @Test
  void invalidProductParametersAreRejectedBeforeCacheProtection() {
    long rejected = count("bloom_rejected"), locks = count("rebuild_requested");
    assertThatThrownBy(() -> catalog.product(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> catalog.product(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThat(count("bloom_rejected")).isEqualTo(rejected);
    assertThat(count("rebuild_requested")).isEqualTo(locks);
  }

  /** SQL 尚未提交且位图丢失，待登记记录仍能在另一个构建者的扫描中恢复该 ID。 */
  @Test
  void pendingPublicationSurvivesBitmapLossAndRebuild() throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              tx -> {
                bloom.registerBeforePublish(9_200_001L);
                redis.delete(bloom.filterKey());
                try {
                  assertThat(executor.submit(bloom::rebuild).get(5, TimeUnit.SECONDS)).isTrue();
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
                assertThat(bloom.mightContain(9_200_001L)).isTrue();
                tx.setRollbackOnly();
              });
    } finally {
      executor.shutdownNow();
    }
  }

  /** 强制本地与禁用本地两条路径都使用实际缓存，不能用热点标记替身冒充命中。 */
  @Test
  void allAndOffPoliciesIsolateRealCacheLayers() {
    var all =
        new CatalogCache(
            properties,
            store,
            hot,
            json,
            metrics,
            bloom,
            protection,
            ttls,
            new com.bitmall.agent.product.config.CatalogLocalProperties("ALL"));
    all.get("layer-probe", String.class, Duration.ofSeconds(10), () -> "database");
    long localBefore = count("local_hit");
    assertThat(all.get("layer-probe", String.class, Duration.ofSeconds(10), () -> "unexpected"))
        .isEqualTo("database");
    assertThat(count("local_hit") - localBefore).isEqualTo(1);
    var off =
        new CatalogCache(
            properties,
            store,
            hot,
            json,
            metrics,
            bloom,
            protection,
            ttls,
            new com.bitmall.agent.product.config.CatalogLocalProperties("OFF"));
    hot.hot.set(true);
    long redisBefore = count("redis_hit");
    assertThat(off.get("layer-probe", String.class, Duration.ofSeconds(10), () -> "unexpected"))
        .isEqualTo("database");
    assertThat(count("redis_hit") - redisBefore).isEqualTo(1);
    assertThat(count("local_hit") - localBefore).isEqualTo(1);
  }
}
