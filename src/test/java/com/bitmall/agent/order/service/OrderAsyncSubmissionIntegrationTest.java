package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.config.*;
import com.bitmall.agent.order.dao.repository.*;
import com.bitmall.agent.order.idempotent.OrderIdempotencyBackend;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.mq.BrokerHealthChecker;
import com.bitmall.agent.order.mq.consumer.OrderBuildConsumer;
import com.bitmall.agent.order.mq.message.*;
import com.bitmall.agent.order.mq.producer.OrderEventProducer;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.net.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 真实 Redis/H2 与真实消费者逻辑，替换 MQ 传输避免向共享开发集群发送测试命令。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:async-submission;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.order-processing.locks-enabled=true",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
@Import(OrderAsyncSubmissionIntegrationTest.ProbeConfiguration.class)
class OrderAsyncSubmissionIntegrationTest {
  private static Process server;
  private static final String namespace = "test:async:" + UUID.randomUUID() + ":";
  private static final AtomicLong ids = new AtomicLong(80000);
  private long sku;
  @Autowired CheckoutService checkout;
  @Autowired OrderTraceService traces;
  @Autowired OrderSubmissionService submissions;
  @Autowired OrderBuildCommandService commands;
  @Autowired OrderRecoveryService recovery;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired OrderService orders;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CommitProbe probe;
  @MockitoSpyBean OrderProcessingProperties processing;
  @MockitoSpyBean OrderMessagingProperties messaging;
  @MockitoBean BrokerHealthChecker broker;
  @Autowired OrderTraceRepository traceRows;
  @MockitoSpyBean OrderMessageRepository outbox;
  @MockitoSpyBean OrderRepository orderRepository;
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

  @BeforeEach
  void setup() {
    sku = ids.incrementAndGet();
    doReturn("ASYNC").when(processing).getSubmissionMode();
    // Spring 的远程 MQ 装配仍关闭，只在策略检查中模拟可用传输。
    doReturn(true).when(messaging).isEnabled();
    when(broker.isHealthy()).thenReturn(true);
    jdbc.sql("DELETE FROM order_message_outbox").update();
    probe.events.clear();
    jdbc.sql(
            "INSERT INTO"
                + " product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)"
                + " SELECT :id,product_id,:code,spec,price,original_price,10,TRUE FROM product_sku"
                + " WHERE id=1003")
        .param("id", sku)
        .param("code", "ASYNC-" + sku)
        .update();
  }

  @Test
  void admissionIsDurableButDoesNotPretendStockOrOrderIsReserved() {
    var p = prepare(null);
    var accepted = submit(p);
    assertThat(accepted.getMode()).isEqualTo("ASYNC");
    assertThat(accepted.getStatus()).isEqualTo("PROCESSING");
    assertThat(accepted.getOrderId()).isNull();
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(ordersFor(p)).isZero();
    assertThat(buildMessages(p)).isEqualTo(1);
    assertThat(tracesRow(p, "processing_expires_at")).isNotNull();
    var acceptedRow = traceRows.find(p.getTraceId()).orElseThrow();
    assertThat(Duration.between(acceptedRow.getAcceptedAt(), acceptedRow.getProcessingExpiresAt()).toSeconds())
        .isBetween(598L, 601L);
    var message = outbox.findReady(10).get(0);
    assertThat(message.getEventType()).isEqualTo("ORDER_BUILD");
    assertThat(message.getPayload())
        .contains(p.getTraceId())
        .doesNotContain("userId", "price", "address");
    assertThat(submit(p).getStatus()).isEqualTo("PROCESSING");
    assertThat(buildMessages(p)).isEqualTo(1);
    assertThat(traceRows.find(p.getTraceId()).orElseThrow().getProcessingExpiresAt())
        .isEqualTo(acceptedRow.getProcessingExpiresAt());
  }

  @Test
  void outboxRelayAndRealConsumerBuildPendingOrderAndReusePaymentLifecycle() throws Exception {
    var p = prepare(null);
    submit(p);
    pump();
    var built = traces.get(10001, p.getTraceId());
    assertThat(built.getStatus()).isEqualTo("SUCCESS");
    assertThat(orders.order(10001, built.getOrderId()).getStatus()).isEqualTo("PENDING_PAYMENT");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
    lifecycle.payDemo(10001, built.getOrderId());
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isZero();
  }

  @Test
  void queuedCouponIsOnlyLockedWhenOrderIsActuallyCreated() {
    long coupon =
        jdbc.sql("SELECT id FROM user_coupon WHERE user_id=10001 AND coupon_id=3001")
            .query(Long.class)
            .single();
    var p = prepare(coupon);
    submit(p);
    assertThat(couponStatus(coupon)).isEqualTo("AVAILABLE");
    commands.consume(p.getTraceId());
    assertThat(couponStatus(coupon)).isEqualTo("LOCKED");
    orders.cancelOrder(10001, traces.get(10001, p.getTraceId()).getOrderId());
    assertThat(couponStatus(coupon)).isEqualTo("AVAILABLE");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
  }

  @Test
  void mqRedeliveryAndLostRedisDedupeStillCreateOneOrder() {
    var p = prepare(null);
    submit(p);
    commands.consume(p.getTraceId());
    commands.consume(p.getTraceId());
    doReturn("DISABLED").when(idempotency).begin(anyString());
    commands.consume(p.getTraceId());
    commands.consume(p.getTraceId());
    assertThat(ordersFor(p)).isEqualTo(1);
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM order_message_outbox WHERE event_type='ORDER_CREATED'"))
        .isEqualTo(1);
  }

  @Test
  void failedOutboxInsertRollsBackAdmissionAndOriginalTraceCanRetry() {
    var p = prepare(null);
    doThrow(new DataAccessResourceFailureException("暂时无法保存命令"))
        .doCallRealMethod()
        .when(outbox)
        .insertBuild(anyString(), eq(p.getTraceId()), anyString(), anyString());
    assertThatThrownBy(() -> submit(p)).isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("INIT");
    assertThat(buildMessages(p)).isZero();
    assertThat(stock()).isEqualTo(10);
    assertThat(submit(p).getStatus()).isEqualTo("PROCESSING");
    assertThat(buildMessages(p)).isEqualTo(1);
  }

  @Test
  void rolledBackOuterTransactionDoesNotDispatchAndSuccessfulCommitDoes() {
    var p = prepare(null);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              submit(p);
              assertThat(probe.events).isEmpty();
              tx.setRollbackOnly();
            });
    assertThat(probe.events).isEmpty();
    assertThat(buildMessages(p)).isZero();
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("INIT");
    submit(p);
    assertThat(probe.events).hasSize(1);
  }

  @Test
  void changedPriceAfterAdmissionFailsWithoutConsumingStock() {
    var p = prepare(null);
    submit(p);
    jdbc.sql("UPDATE product_sku SET price=price+1 WHERE id=:id").param("id", sku).update();
    commands.consume(p.getTraceId());
    assertThat(traces.get(10001, p.getTraceId()).getFailureCode()).isEqualTo("PRICE_CHANGED");
    assertThat(ordersFor(p)).isZero();
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(buildStatus(p)).isEqualTo("CANCELLED");
  }

  @Test
  void transientConsumerFailureRollsBackAndMessageCanRetry() {
    var p = prepare(null);
    submit(p);
    doThrow(new DataAccessResourceFailureException("数据库暂不可用"))
        .doCallRealMethod()
        .when(orderRepository)
        .attachTrace(anyString(), eq(p.getTraceId()));
    assertThatThrownBy(() -> commands.consume(p.getTraceId()))
        .isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("PROCESSING");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(ordersFor(p)).isZero();
    commands.consume(p.getTraceId());
    assertThat(ordersFor(p)).isEqualTo(1);
    assertThat(stock()).isEqualTo(9);
  }

  @Test
  void processingDeadlinePreventsLateMessagesFromCreatingOrders() {
    var p = prepare(null);
    submit(p);
    expire(p);
    commands.consume(p.getTraceId());
    assertThat(traces.get(10001, p.getTraceId()).getFailureCode()).isEqualTo("QUEUE_TIMEOUT");
    assertThat(ordersFor(p)).isZero();
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void recoveryCancelsTimedOutCommandAndDelayedDeliveryIsHarmless() {
    var p = prepare(null);
    submit(p);
    expire(p);
    recovery.reconcile(p.getTraceId());
    assertThat(buildStatus(p)).isEqualTo("CANCELLED");
    commands.consume(p.getTraceId());
    assertThat(ordersFor(p)).isZero();
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void recoveryResendsSameCommandWhenSentTaskDidNotFinish() throws Exception {
    var p = prepare(null);
    submit(p);
    String event = outbox.findReady(10).get(0).getEventId();
    jdbc.sql("UPDATE order_message_outbox SET status='SENT',sent_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", p.getTraceId())
        .update();
    recovery.reconcile(p.getTraceId());
    assertThat(outbox.findReady(10).get(0).getEventId()).isEqualTo(event);
    pump();
    assertThat(ordersFor(p)).isEqualTo(1);
  }

  @Test
  void newSyncModeDoesNotChangePreviouslyAcceptedAsyncTrace() {
    var p = prepare(null);
    submit(p);
    doReturn("SYNC").when(processing).getSubmissionMode();
    assertThat(submit(p).getStatus()).isEqualTo("PROCESSING");
    assertThat(ordersFor(p)).isZero();
    commands.consume(p.getTraceId());
    assertThat(ordersFor(p)).isEqualTo(1);
    var second = prepare(null);
    assertThat(second.getMode()).isEqualTo("SYNC");
    assertThat(submit(second).getStatus()).isEqualTo("SUCCESS");
    assertThat(stock()).isEqualTo(8);
  }

  @Test
  void syncAndAsyncCompeteForTheSameLastItemWithoutTwoInventoryPools() {
    jdbc.sql("UPDATE product_sku SET stock=1 WHERE id=:id").param("id", sku).update();
    var async = prepare(null);
    doReturn("SYNC").when(processing).getSubmissionMode();
    var sync = prepare(null);
    submit(async);
    assertThat(submit(sync).getStatus()).isEqualTo("SUCCESS");
    commands.consume(async.getTraceId());
    assertThat(traces.get(10001, async.getTraceId()).getStatus()).isEqualTo("FAILED");
    assertThat(stock()).isZero();
    assertThat(locked()).isEqualTo(1);
    assertThat(ordersFor(async) + ordersFor(sync)).isEqualTo(1);
  }

  @Test
  void unavailableQueueKeepsOriginalTraceRetryable() {
    var p = prepare(null);
    doReturn(false).when(messaging).isEnabled();
    assertThatThrownBy(() -> submit(p)).hasMessageContaining("暂未开放");
    assertThat(traces.get(10001, p.getTraceId()).getStatus()).isEqualTo("INIT");
    assertThat(buildMessages(p)).isZero();
    doReturn(true).when(messaging).isEnabled();
    assertThat(submit(p).getStatus()).isEqualTo("PROCESSING");
  }

  @Test
  void consumerCannotBuildUnconfirmedOrForeignUserRequests() {
    var p = prepare(null);
    assertThatThrownBy(() -> submissions.submit(10002, p.getTraceId())).hasMessageContaining("不属于");
    assertThatThrownBy(() -> commands.consume(p.getTraceId())).hasMessageContaining("尚未可靠受理");
    assertThat(ordersFor(p)).isZero();
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void confirmationExpiryDoesNotOverrideAcceptedProcessingDeadline() {
    var p = prepare(null);
    submit(p);
    jdbc.sql("UPDATE order_submit_trace SET expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", p.getTraceId())
        .update();
    commands.consume(p.getTraceId());
    assertThat(ordersFor(p)).isEqualTo(1);
  }

  private void pump() throws Exception {
    OrderEventProducer producer = mock(OrderEventProducer.class);
    var consumer = new OrderBuildConsumer(json, commands);
    when(producer.send(any()))
        .thenAnswer(
            call -> {
              com.bitmall.agent.order.dao.entity.OrderMessageDO message = call.getArgument(0);
              if ("ORDER_BUILD".equals(message.getEventType()))
                consumer.onMessage(message.getPayload());
              return "isolated-broker-id";
            });
    new OrderMessageRelayService(outbox, producer, messaging).relayPendingMessages();
  }

  private OrderSubmitVO prepare(Long coupon) {
    var quote = checkout.checkoutQuote(10001, sku, 1, 20001L, coupon);
    return traces.prepare(
        10001,
        new CreateOrderCommand("DIRECT", sku, 1, 20001L, coupon, quote.getPayAmount()),
        null);
  }

  private OrderSubmitVO submit(OrderSubmitVO p) {
    return submissions.submit(10001, p.getTraceId());
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private long ordersFor(OrderSubmitVO p) {
    return jdbc.sql("SELECT COUNT(*) FROM customer_order WHERE submit_trace_id=:id")
        .param("id", p.getTraceId())
        .query(Long.class)
        .single();
  }

  private long buildMessages(OrderSubmitVO p) {
    return jdbc.sql(
            "SELECT COUNT(*) FROM order_message_outbox WHERE event_type='ORDER_BUILD' AND"
                + " trace_id=:id")
        .param("id", p.getTraceId())
        .query(Long.class)
        .single();
  }

  private String buildStatus(OrderSubmitVO p) {
    return jdbc.sql("SELECT status FROM order_message_outbox WHERE trace_id=:id")
        .param("id", p.getTraceId())
        .query(String.class)
        .single();
  }

  private Object tracesRow(OrderSubmitVO p, String column) {
    return jdbc.sql("SELECT " + column + " FROM order_submit_trace WHERE trace_id=:id")
        .param("id", p.getTraceId())
        .query(Object.class)
        .single();
  }

  private String couponStatus(long id) {
    return jdbc.sql("SELECT status FROM user_coupon WHERE id=:id")
        .param("id", id)
        .query(String.class)
        .single();
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

  private void expire(OrderSubmitVO p) {
    jdbc.sql("UPDATE order_submit_trace SET processing_expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", p.getTraceId())
        .update();
  }

  /** 测试探针使用真实事务事件监听，证明回滚前不会向外部发送。 */
  static class CommitProbe {
    final Queue<String> events = new ConcurrentLinkedQueue<>();

    @TransactionalEventListener
    void committed(OutboxReadyEvent event) {
      events.add(event.getEventId());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean
    CommitProbe commitProbe() {
      return new CommitProbe();
    }
  }
}
