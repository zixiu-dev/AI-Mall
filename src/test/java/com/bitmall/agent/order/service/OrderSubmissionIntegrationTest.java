package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.idempotent.OrderIdempotencyBackend;
import com.bitmall.agent.order.idempotent.OrderRedisClient;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.net.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 真实 Redis 锁与 H2 事务的同步购买测试：验证重试、并发库存、失败回滚和待支付生命周期。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sync-submission;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.order-processing.locks-enabled=true",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class OrderSubmissionIntegrationTest {
  private static Process server;
  private static final AtomicLong ids = new AtomicLong(50000);
  private static final String namespace = "test:sync:" + UUID.randomUUID() + ":";
  private long sku;
  @Autowired CheckoutService checkout;
  @Autowired OrderTraceService traces;
  @Autowired OrderSubmissionService submissions;
  @Autowired OrderRecoveryService recovery;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired OrderService orders;
  @Autowired OrderRedisClient redis;
  @Autowired JdbcClient jdbc;
  @Autowired MockMvc mvc;
  @MockitoSpyBean OrderRepository orderMapper;
  @MockitoSpyBean OrderIdempotencyBackend idempotency;

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    server =
        new ProcessBuilder(
                System.getenv().getOrDefault("REDIS_SERVER", "redis-server"),
                "--bind",
                "127.0.0.1",
                "--port",
                String.valueOf(port),
                "--save",
                "",
                "--appendonly",
                "no",
                "--maxmemory-policy",
                "noeviction",
                "--dir",
                Files.createTempDirectory("order-redis-").toString())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (true) {
      try (Socket ignored = new Socket("127.0.0.1", port)) {
        break;
      } catch (Exception ex) {
        if (System.nanoTime() > deadline) throw ex;
        Thread.sleep(25);
      }
    }
    registry.add("app.order-processing.redis-port", () -> port);
    registry.add("app.order-processing.namespace", () -> namespace);
  }

  /** 测试结束后停止自己的 Redis。 */
  @AfterAll
  static void stopRedis() {
    if (server != null) server.destroy();
  }

  /** 每例独立 SKU，避免测试顺序改变库存竞争结果。 */
  @BeforeEach
  void setup() {
    sku = ids.incrementAndGet();
    jdbc.sql(
            "INSERT INTO"
                + " product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)"
                + " SELECT :id,product_id,:code,spec,price,original_price,10,TRUE FROM product_sku"
                + " WHERE id=1003")
        .param("id", sku)
        .param("code", "SYNC-" + sku)
        .update();
  }

  @Test
  void distributedLockReallyExcludesConcurrentThreads() throws Exception {
    try (var lease = idempotency.lock("proof")) {
      assertThat(redis.template().hasKey(namespace + "idempotent:proof")).isTrue();
      var pool = Executors.newSingleThreadExecutor();
      try {
        pool.submit(
                () ->
                    assertThatThrownBy(() -> idempotency.lock("proof"))
                        .isInstanceOf(
                            com.bitmall.agent.common.idempotent.IdempotentBusyException.class))
            .get(5, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }
    }
  }

  @Test
  void prepareDoesNotReserveAndNewPurchaseHasANewTrace() {
    var a = prepare(null);
    var b = prepare(null);
    assertThat(a.getTraceId()).isNotEqualTo(b.getTraceId());
    assertThat(a.getStatus()).isEqualTo("INIT");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM customer_order WHERE submit_trace_id='"
                    + a.getTraceId()
                    + "'"))
        .isZero();
  }

  @Test
  void sequentialRetryReturnsSamePendingOrderAndRepurchaseIsAllowed() {
    var a = submit(prepare(null));
    var again = submissions.submit(10001, a.getTraceId());
    assertThat(a.getStatus()).isEqualTo("SUCCESS");
    assertThat(again.getOrderId()).isEqualTo(a.getOrderId());
    assertThat(orders.order(10001, a.getOrderId()).getStatus()).isEqualTo("PENDING_PAYMENT");
    assertThat(submit(prepare(null)).getOrderId()).isNotEqualTo(a.getOrderId());
    assertThat(stock()).isEqualTo(8);
    assertThat(locked()).isEqualTo(2);
  }

  /** 模拟 Redis 锁完全失效，数据库仍只能让同一 trace 创建一笔订单。 */
  @Test
  void databaseProtectsConcurrentReplayWhenLockIsLost() throws Exception {
    doReturn((AutoCloseable) () -> {}).when(idempotency).lock(anyString());
    var prepared = prepare(null);
    var results =
        race(
            java.util.Collections.nCopies(
                12, () -> submissions.submit(10001, prepared.getTraceId())));
    assertThat(results).allMatch(v -> v.getStatus().equals("SUCCESS"));
    assertThat(results.stream().map(OrderSubmitVO::getOrderId).distinct().count()).isEqualTo(1);
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
  }

  @Test
  void lastItemCannotBeOversoldAcrossDifferentTraces() throws Exception {
    jdbc.sql("UPDATE product_sku SET stock=1 WHERE id=:id").param("id", sku).update();
    var prepared = new ArrayList<OrderSubmitVO>();
    for (int i = 0; i < 12; i++) prepared.add(prepare(null));
    var calls = new ArrayList<Callable<OrderSubmitVO>>();
    for (var p : prepared) calls.add(() -> submissions.submit(10001, p.getTraceId()));
    var results = race(calls);
    assertThat(results.stream().filter(v -> "SUCCESS".equals(v.getStatus())).count()).isEqualTo(1);
    assertThat(stock()).isZero();
    assertThat(locked()).isEqualTo(1);
  }

  @Test
  void couponIsLockedAtCreationReleasedAtCancellationOnlyOnce() {
    long coupon = coupon();
    var result = submit(prepare(coupon));
    assertThat(couponStatus(coupon)).isEqualTo("LOCKED");
    orders.cancelOrder(10001, result.getOrderId());
    orders.cancelOrder(10001, result.getOrderId());
    assertThat(couponStatus(coupon)).isEqualTo("AVAILABLE");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(submissions.submit(10001, result.getTraceId()).getOrderId())
        .isEqualTo(result.getOrderId());
  }

  @Test
  void sameCouponCannotBeOccupiedByTwoPurchases() throws Exception {
    long coupon = coupon();
    var a = prepare(coupon);
    var b = prepare(coupon);
    var results = race(List.of(() -> submit(a), () -> submit(b)));
    assertThat(results.stream().filter(v -> "SUCCESS".equals(v.getStatus())).count()).isEqualTo(1);
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
    assertThat(couponStatus(coupon)).isEqualTo("LOCKED");
  }

  @Test
  void failureAfterAllBusinessWritesRollsBackButFailureTraceSurvives() {
    long coupon = coupon();
    var p = prepare(coupon);
    doThrow(new OrderRejectedException("TEST_REJECTED", "测试事务中途拒绝"))
        .when(orderMapper)
        .attachTrace(anyString(), eq(p.getTraceId()));
    var result = submit(p);
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getFailureCode()).isEqualTo("TEST_REJECTED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(couponStatus(coupon)).isEqualTo("AVAILABLE");
    assertThat(count("SELECT COUNT(*) FROM order_item WHERE sku_id=" + sku)).isZero();
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("FAILED");
  }

  @Test
  void infrastructureFailureDoesNotInventBusinessFailureAndCanRetryOriginalTrace() {
    var p = prepare(null);
    doThrow(new DataAccessResourceFailureException("暂时无法提交"))
        .doCallRealMethod()
        .when(orderMapper)
        .attachTrace(anyString(), eq(p.getTraceId()));
    assertThatThrownBy(() -> submit(p)).isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("INIT");
    assertThat(stock()).isEqualTo(10);
    assertThat(submit(p).getStatus()).isEqualTo("SUCCESS");
    assertThat(stock()).isEqualTo(9);
  }

  @Test
  void changedPriceRequiresNewConfirmationWithoutOccupyingStock() {
    var p = prepare(null);
    jdbc.sql("UPDATE product_sku SET price=price+1 WHERE id=:id").param("id", sku).update();
    var result = submit(p);
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getFailureCode()).isEqualTo("PRICE_CHANGED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
  }

  @Test
  void snapshotHashIsCheckedAndNotUsedAsPurchaseIdentifier() {
    var p = prepare(null);
    jdbc.sql("UPDATE order_submit_trace SET request_hash=:hash WHERE trace_id=:id")
        .param("hash", "0".repeat(64))
        .param("id", p.getTraceId())
        .update();
    assertThat(submit(p).getFailureCode()).isEqualTo("SNAPSHOT_INVALID");
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void foreignUserCannotSubmitOrReadTrace() {
    var p = prepare(null);
    assertThatThrownBy(() -> submissions.submit(10002, p.getTraceId())).hasMessageContaining("不属于");
    assertThatThrownBy(() -> traces.get(10002, p.getTraceId())).hasMessageContaining("不属于");
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("INIT");
  }

  @Test
  void expiredTraceCannotCreateAnOrder() {
    var p = prepare(null);
    jdbc.sql("UPDATE order_submit_trace SET expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", p.getTraceId())
        .update();
    assertThat(submit(p).getStatus()).isEqualTo("EXPIRED");
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void expiryScanCannotOverwriteSuccessfulTrace() {
    var p = submit(prepare(null));
    recovery.reconcile(p.getTraceId());
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("SUCCESS");
  }

  @Test
  void paymentOnlyConfirmsLockedStockAndConsumesCouponOnce() {
    long coupon = coupon();
    var p = submit(prepare(coupon));
    lifecycle.payDemo(10001, p.getOrderId());
    lifecycle.payDemo(10001, p.getOrderId());
    assertThat(orders.order(10001, p.getOrderId()).getStatus()).isEqualTo("PAID");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isZero();
    assertThat(couponStatus(coupon)).isEqualTo("USED");
    assertThatThrownBy(() -> orders.cancelOrder(10001, p.getOrderId())).hasMessageContaining("售后");
    assertThat(stock()).isEqualTo(9);
  }

  @Test
  void timeoutClosesAndReleasesOnceAndLatePaymentIsRejected() {
    long coupon = coupon();
    var p = submit(prepare(coupon));
    expireOrder(p.getOrderId());
    lifecycle.closeExpired(10001, p.getOrderId());
    lifecycle.closeExpired(10001, p.getOrderId());
    assertThat(orders.order(10001, p.getOrderId()).getStatus()).isEqualTo("CLOSED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(couponStatus(coupon)).isEqualTo("AVAILABLE");
    assertThatThrownBy(() -> lifecycle.payDemo(10001, p.getOrderId())).hasMessageContaining("不能支付");
  }

  @Test
  void paymentAndCancellationRaceHasExactlyOneInventoryOutcome() throws Exception {
    long coupon = coupon();
    var p = submit(prepare(coupon));
    race(
        List.of(
            () -> {
              try {
                lifecycle.payDemo(10001, p.getOrderId());
              } catch (IllegalStateException expected) {
              }
              return p;
            },
            () -> {
              try {
                orders.cancelOrder(10001, p.getOrderId());
              } catch (IllegalStateException expected) {
              }
              return p;
            }));
    String status = orders.order(10001, p.getOrderId()).getStatus();
    assertThat(status).isIn("PAID", "CANCELLED");
    assertThat(locked()).isZero();
    assertThat(stock()).isEqualTo("PAID".equals(status) ? 9 : 10);
    assertThat(couponStatus(coupon)).isEqualTo("PAID".equals(status) ? "USED" : "AVAILABLE");
  }

  @Test
  void paymentAndTimeoutRaceNeverPaysAnExpiredOrder() throws Exception {
    var p = submit(prepare(null));
    expireOrder(p.getOrderId());
    race(
        List.of(
            () -> {
              try {
                lifecycle.payDemo(10001, p.getOrderId());
              } catch (IllegalStateException expected) {
              }
              return p;
            },
            () -> {
              lifecycle.closeExpired(10001, p.getOrderId());
              return p;
            }));
    assertThat(orders.order(10001, p.getOrderId()).getStatus()).isEqualTo("CLOSED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
  }

  @Test
  void httpRequiresBackendTraceAndActivityEndpointIsRemoved() throws Exception {
    mvc.perform(
            post("/api/store/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuId\":1001,\"quantity\":1}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/store/orders/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceId\":\"not-a-trace\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/order-activities")).andExpect(status().isNotFound());
    mvc.perform(get("/api/store/order-options"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demoPaymentEnabled").value(true));
  }

  @Test
  void formerlyBoundSkuCanBeBoughtNormallyAndActivityTableIsGone() {
    var quote = checkout.checkoutQuote(10001, 1004, 1, null);
    var p =
        traces.prepare(
            10001,
            new CreateOrderCommand("DIRECT", 1004L, 1, null, null, quote.getPayAmount()),
            null);
    var result = submit(p);
    assertThat(result.getMode()).isEqualTo("SYNC");
    orders.cancelOrder(10001, result.getOrderId());
    assertThat(
            count(
                "SELECT COUNT(*) FROM information_schema.tables WHERE"
                    + " table_name='order_sale_activity'"))
        .isZero();
  }

  private OrderSubmitVO prepare(Long coupon) {
    var quote = checkout.checkoutQuote(10001, sku, 1, 20001L, coupon);
    return traces.prepare(
        10001,
        new CreateOrderCommand("DIRECT", sku, 1, 20001L, coupon, quote.getPayAmount()),
        null);
  }

  private OrderSubmitVO submit(OrderSubmitVO trace) {
    return submissions.submit(10001, trace.getTraceId());
  }

  private int stock() {
    return jdbc.sql("SELECT stock FROM product_sku WHERE id=:id")
        .param("id", sku)
        .query(Integer.class)
        .single();
  }

  private int locked() {
    return jdbc.sql("SELECT locked_stock FROM product_sku WHERE id=:id")
        .param("id", sku)
        .query(Integer.class)
        .single();
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private void expireOrder(String id) {
    jdbc.sql("UPDATE customer_order SET payment_expires_at=:at WHERE id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", id)
        .update();
  }

  private long coupon() {
    long id = ids.incrementAndGet();
    jdbc.sql(
            "INSERT INTO"
                + " coupon(id,name,threshold_amount,discount_amount,total_stock,claimed_count,start_at,end_at,status)"
                + " VALUES (:id,'同步测试券',0,10,10,1,:start,:end,'ACTIVE')")
        .param("id", id)
        .param("start", LocalDateTime.now().minusDays(1))
        .param("end", LocalDateTime.now().plusDays(1))
        .update();
    jdbc.sql("INSERT INTO user_coupon(user_id,coupon_id,status) VALUES (10001,:id,'AVAILABLE')")
        .param("id", id)
        .update();
    return jdbc.sql("SELECT id FROM user_coupon WHERE user_id=10001 AND coupon_id=:id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private String couponStatus(long id) {
    return jdbc.sql("SELECT status FROM user_coupon WHERE id=:id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private List<OrderSubmitVO> race(List<Callable<OrderSubmitVO>> tasks) throws Exception {
    var pool = Executors.newFixedThreadPool(Math.min(12, tasks.size()));
    var gate = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<OrderSubmitVO>>();
      for (var task : tasks)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return task.call();
                }));
      gate.countDown();
      var results = new ArrayList<OrderSubmitVO>();
      for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
      return results;
    } finally {
      pool.shutdownNow();
    }
  }
}
