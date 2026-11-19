package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.config.*;
import com.bitmall.agent.order.controller.OrderController;
import com.bitmall.agent.order.dao.repository.*;
import com.bitmall.agent.order.idempotent.OrderRedisClient;
import com.bitmall.agent.order.inventory.*;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.mq.BrokerHealthChecker;
import com.bitmall.agent.order.mq.consumer.OrderBuildDeadLetterConsumer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 使用真实 Redis 执行 Lua，使用真实事务检验库存边界；不向共享 RocketMQ 集群发送测试消息。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:inventory-reservation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.inventory.enabled=true",
      "app.order-processing.locks-enabled=true",
      "app.order-processing.recovery-interval-ms=3600000"
    })
@ActiveProfiles("demo")
class InventoryReservationIntegrationTest {
  private static Process server;
  private static final AtomicLong ids = new AtomicLong(95000);
  private static final String namespace = "test:inventory:" + UUID.randomUUID() + ":";
  private long sku;
  @Autowired com.bitmall.agent.product.service.ProductService catalog;
  @Autowired OrderController controller;
  @Autowired com.bitmall.agent.order.monitor.OrderMetrics metrics;
  @Autowired CheckoutService checkout;
  @Autowired OrderTraceService traces;
  @Autowired OrderTraceRepository traceRows;
  @Autowired OrderSubmissionService submissions;
  @Autowired OrderBuildCommandService consumer;
  @Autowired OrderRecoveryService recovery;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired OrderInventoryService inventory;
  @MockitoSpyBean com.bitmall.agent.order.dao.mapper.OrderInventoryMapper inventoryRows;
  @Autowired InventorySettlementService settlement;
  @MockitoSpyBean InventoryRedisStore redis;
  @Autowired OrderRedisClient client;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;
  @MockitoSpyBean OrderProcessingProperties processing;
  @MockitoSpyBean OrderMessagingProperties messaging;
  @MockitoBean BrokerHealthChecker broker;
  @MockitoSpyBean OrderMessageRepository outbox;
  @MockitoSpyBean OrderRepository orders;
  @MockitoSpyBean InventoryReservationProperties settings;

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
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
                Files.createTempDirectory("inventory-redis-").toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectErrorStream(true)
            .start();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (true) {
      try (var socket = new Socket("127.0.0.1", port)) {
        break;
      } catch (Exception ex) {
        if (System.nanoTime() > deadline) throw ex;
        Thread.sleep(25);
      }
    }
    registry.add("app.order-processing.redis-port", () -> port);
    registry.add("app.order-processing.namespace", () -> namespace);
  }

  @AfterAll
  static void stop() {
    if (server != null) server.destroy();
  }

  @BeforeEach
  void setup() {
    sku = ids.incrementAndGet();
    doReturn("ASYNC").when(processing).getSubmissionMode();
    doReturn(true).when(messaging).isEnabled();
    when(broker.isHealthy()).thenReturn(true);
    jdbc.sql(
            "INSERT INTO"
                + " product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)"
                + " SELECT :id,product_id,:code,spec,price,original_price,10,TRUE FROM product_sku"
                + " WHERE id=1003")
        .param("id", sku)
        .param("code", "RESERVE-" + sku)
        .update();
  }

  @Test
  void detailShowsReservationBeforeDatabaseDeductionWithoutInitializingOnRead() {
    long product = sku;
    jdbc.sql(
            "INSERT INTO"
                + " product(id,name,category,brand,summary,image_key,rating,sales_count,enabled)"
                + " SELECT :id,name,category,brand,summary,image_key,rating,sales_count,TRUE FROM"
                + " product WHERE id=101")
        .param("id", product)
        .update();
    jdbc.sql("UPDATE product_sku SET product_id=:id WHERE id=:id").param("id", product).update();
    java.util.function.IntSupplier displayed =
        () ->
            catalog.product(product).getSkus().stream()
                .filter(v -> v.getId() == sku)
                .findFirst()
                .orElseThrow()
                .getStock();
    assertThat(displayed.getAsInt()).isEqualTo(10);
    assertThat(redis.poolExists(sku)).isFalse();
    assertThat(submissions.submit(10001, controller.issue().get("traceId"), command()).getStatus())
        .isEqualTo("PROCESSING");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=:id")
                .param("id", sku)
                .query(Integer.class)
                .single())
        .isEqualTo(10);
    assertThat(displayed.getAsInt()).isEqualTo(9);
    assertAvailable(9);
    client.template().delete(redis.availableKey(sku));
    assertThatThrownBy(displayed::getAsInt)
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(redis.poolExists(sku)).isFalse();
  }

  @Test
  void issuerOnlyCreatesUuidAndFirstSubmissionBindsImmutablePurchase() {
    long before = count("SELECT COUNT(*) FROM order_submit_trace");
    String id = controller.issue().get("traceId");
    assertThat(UUID.fromString(id)).isNotNull();
    assertThat(count("SELECT COUNT(*) FROM order_submit_trace")).isEqualTo(before);
    var command = command();
    assertThat(submissions.submit(10001, id, command).getStatus()).isEqualTo("PROCESSING");
    assertThat(traceRows.find(id).orElseThrow().getSubmissionHash()).hasSize(64);
    assertThat(submissions.submit(10001, id, command).getStatus()).isEqualTo("PROCESSING");
    var changed =
        new CreateOrderCommand("DIRECT", sku, 2, 20001L, null, command.getExpectedPayAmount());
    assertThatThrownBy(() -> submissions.submit(10001, id, changed)).hasMessageContaining("不能更改");
    assertThatThrownBy(() -> submissions.submit(10002, id, command)).hasMessageContaining("不属于");
    assertAvailable(9);
    assertThat(builds(id)).isEqualTo(1);
  }

  @Test
  void preDeductionHappensBeforeMqAndRepeatedAdmissionDoesNotDeductAgain() {
    double createdBefore = ((Number) metrics.snapshot().get("ordersCreatedTotal")).doubleValue();
    String id = prepare();
    assertThat(submissions.submit(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertAvailable(9);
    assertThat(state(id)).isEqualTo("RESERVED");
    assertThat(client.template().hasKey(legacyPendingKey())).isFalse();
    assertThat(client.template().opsForHash().get(redis.reservationKey(sku, id), "quantity"))
        .isEqualTo("1");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(orderCount(id)).isZero();
    assertThat(((Number) metrics.snapshot().get("ordersCreatedTotal")).doubleValue())
        .isEqualTo(createdBefore);
    submissions.submit(10001, id);
    assertAvailable(9);
    assertThat(builds(id)).isEqualTo(1);
    consumer.consume(id);
    settlement.settleTrace(id);
    consumer.consume(id);
    settlement.settleTrace(id);
    assertAvailable(9);
    assertThat(state(id)).isEqualTo("CONFIRMED");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
    assertThat(orderCount(id)).isEqualTo(1);
    assertThat(((Number) metrics.snapshot().get("ordersCreatedTotal")).doubleValue())
        .isEqualTo(createdBefore + 1);
  }

  @Test
  void queuedReservationCanFinishAfterUpgradeWithoutReadingLegacyPendingCounter() {
    String id = prepare();
    assertThat(submissions.submit(10001, id).getStatus()).isEqualTo("PROCESSING");
    // 旧版本写出的可售量和预占 Hash 格式相同，另带一个 SKU 汇总键。
    client.template().opsForValue().set(legacyPendingKey(), "1");

    consumer.consume(id);
    settlement.settleTrace(id);
    String order = traces.get(10001, id).getOrderId();
    assertThat(state(id)).isEqualTo("CONFIRMED");
    assertAvailable(9);
    assertThat(stock()).isEqualTo(9);

    lifecycle.cancel(10001, order);
    settlement.settleReturn(order, sku);
    settlement.settleReturn(order, sku);
    assertThat(state(id)).isEqualTo("RETURNED");
    assertAvailable(10);
    assertThat(stock()).isEqualTo(10);
    assertThat(client.template().opsForValue().get(legacyPendingKey())).isEqualTo("1");
  }

  @Test
  void lastItemIsSharedBySyncAndAsync() {
    jdbc.sql("UPDATE product_sku SET stock=1 WHERE id=:id").param("id", sku).update();
    String queued = prepare();
    doReturn("SYNC").when(processing).getSubmissionMode();
    String sync = prepare();
    submissions.submit(10001, queued);
    assertThat(submissions.submit(10001, sync).getFailureCode()).isEqualTo("OUT_OF_STOCK");
    settlement.settleTrace(sync);
    consumer.consume(queued);
    settlement.settleTrace(queued);
    assertAvailable(0);
    assertThat(stock()).isZero();
    assertThat(orderCount(queued) + orderCount(sync)).isEqualTo(1);
  }

  @Test
  void outboxFailureLeavesRecoverableIntentAndRetryUsesSameReservation() {
    String id = prepare();
    doThrow(new DataAccessResourceFailureException("模拟受理写入中断"))
        .doCallRealMethod()
        .when(outbox)
        .insertBuild(anyString(), eq(id), anyString(), anyString());
    assertThatThrownBy(() -> submissions.submit(10001, id))
        .isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(traces.get(10001, id).getStatus()).isEqualTo("INIT");
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("RESERVING");
    assertThat(builds(id)).isZero();
    assertAvailable(9);
    submissions.submit(10001, id);
    assertAvailable(9);
    assertThat(builds(id)).isEqualTo(1);
  }

  @Test
  void abandonedPreDeductionIsReleasedOnceAndLateCallerCannotReserve() {
    String id = prepare();
    inventory.reserve(10001, id);
    assertAvailable(9);
    jdbc.sql("UPDATE order_submit_trace SET next_check_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", id)
        .update();
    recovery.reconcile(id);
    recovery.reconcile(id);
    settlement.settleTrace(id);
    assertThat(traces.get(10001, id).getFailureCode()).isEqualTo("ADMISSION_INTERRUPTED");
    assertAvailable(10);
    assertThat(state(id)).isEqualTo("RELEASED");
    assertThat(builds(id)).isZero();
    assertThatThrownBy(() -> redis.reserve(sku, id, 1)).hasMessageContaining("已结束");
    assertAvailable(10);
  }

  @Test
  void definitiveConsumerFailureReturnsStockAndLateDeliveryCannotCreateOrder() {
    String id = prepare();
    submissions.submit(10001, id);
    jdbc.sql("UPDATE product_sku SET price=price+1 WHERE id=:id").param("id", sku).update();
    consumer.consume(id);
    settlement.settleTrace(id);
    consumer.consume(id);
    settlement.settleTrace(id);
    assertThat(traces.get(10001, id).getFailureCode()).isEqualTo("PRICE_CHANGED");
    assertAvailable(10);
    assertThat(orderCount(id)).isZero();
    assertThat(stock()).isEqualTo(10);
  }

  @Test
  void transientDatabaseFailureKeepsReservationForRetry() {
    String id = prepare();
    submissions.submit(10001, id);
    doThrow(new DataAccessResourceFailureException("模拟数据库临时故障"))
        .doCallRealMethod()
        .when(orders)
        .attachTrace(anyString(), eq(id));
    assertThatThrownBy(() -> consumer.consume(id))
        .isInstanceOf(DataAccessResourceFailureException.class);
    assertAvailable(9);
    assertThat(stock()).isEqualTo(10);
    assertThat(orderCount(id)).isZero();
    assertThat(traces.get(10001, id).getStatus()).isEqualTo("PROCESSING");
    consumer.consume(id);
    settlement.settleTrace(id);
    assertAvailable(9);
    assertThat(stock()).isEqualTo(9);
    assertThat(orderCount(id)).isEqualTo(1);
  }

  @Test
  void cancelledOrderReturnsBothInventoriesExactlyOnce() {
    String id = prepare();
    submissions.submit(10001, id);
    consumer.consume(id);
    settlement.settleTrace(id);
    String order = traces.get(10001, id).getOrderId();
    lifecycle.cancel(10001, order);
    settlement.settleReturn(order, sku);
    lifecycle.cancel(10001, order);
    settlement.settleReturn(order, sku);
    assertAvailable(10);
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(state(id)).isEqualTo("RETURNED");
  }

  @Test
  void paidOrderDoesNotDeductOrReturnRedisStockAgain() {
    String id = prepare();
    submissions.submit(10001, id);
    consumer.consume(id);
    settlement.settleTrace(id);
    String order = traces.get(10001, id).getOrderId();
    lifecycle.payDemo(10001, order);
    lifecycle.payDemo(10001, order);
    assertAvailable(9);
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isZero();
    assertThat(state(id)).isEqualTo("CONFIRMED");
  }

  @Test
  void lostInitializedPoolFailsClosedWithoutDatabaseBypassOrBlindRebuild() {
    String first = prepare();
    submissions.submit(10001, first);
    client.template().opsForValue().set(legacyPendingKey(), "1");
    client.template().delete(redis.availableKey(sku));
    String second = prepare();
    assertThatThrownBy(() -> submissions.submit(10001, second))
        .isInstanceOf(IllegalStateException.class);
    assertThat(orderCount(second)).isZero();
    assertThat(builds(second)).isZero();
    assertThat(stock()).isEqualTo(10);
    assertThat(client.template().hasKey(redis.availableKey(sku))).isFalse();
  }

  @Test
  void releaseTombstonePreventsLateDeductionEvenIfReservationNeverStarted() {
    String id = prepare();
    inventory.reserve(10001, id);
    String late = UUID.randomUUID().toString();
    redis.finish(sku, late, 1, "RELEASE");
    redis.finish(sku, late, 1, "RELEASE");
    assertThatThrownBy(() -> redis.reserve(sku, late, 1)).hasMessageContaining("已结束");
    assertAvailable(9);
  }

  @Test
  void legacyUnpaidOrderCanCloseAfterPoolInitializationWithoutExtraDeduction() {
    doReturn(false).when(settings).isEnabled();
    doReturn("SYNC").when(processing).getSubmissionMode();
    String id = prepare();
    String order = submissions.submit(10001, id).getOrderId();
    assertThat(stock()).isEqualTo(9);
    doReturn(true).when(settings).isEnabled();
    lifecycle.cancel(10001, order);
    settlement.settleReturn(order, sku);
    assertAvailable(10);
    assertThat(stock()).isEqualTo(10);
    assertThat(
            client.template().opsForHash().get(redis.reservationKey(sku, "legacy:" + order), "state"))
        .isEqualTo("RETURNED");
  }

  @Test
  void timedOutQueuedTaskReleasesPreDeductionAndRejectsLateMessage() {
    String id = prepare();
    submissions.submit(10001, id);
    jdbc.sql("UPDATE order_submit_trace SET processing_expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", id)
        .update();
    recovery.reconcile(id);
    consumer.consume(id);
    settlement.settleTrace(id);
    assertAvailable(10);
    assertThat(traces.get(10001, id).getFailureCode()).isEqualTo("QUEUE_TIMEOUT");
    assertThat(orderCount(id)).isZero();
  }

  @Test
  void verifiedDeadLetterRequeuesWithinDeadlineAndReleasesOnlyOnceAfterDeadline() {
    String id = prepare();
    submissions.submit(10001, id);
    String eventId = buildEventId(id);
    var originalDeadline = traceRows.find(id).orElseThrow().getProcessingExpiresAt();
    jdbc.sql("UPDATE order_message_outbox SET status='SENT',sent_at=:at WHERE event_id=:event")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("event", eventId)
        .update();
    var listener = new OrderBuildDeadLetterConsumer(json, recovery);
    listener.onMessage(buildDeadLetter(id, eventId));
    assertThat(outbox.findById(eventId)).isPresent();
    assertThat(jdbc.sql("SELECT status FROM order_message_outbox WHERE event_id=:event")
        .param("event", eventId).query(String.class).single()).isEqualTo("PENDING");
    assertThat(traceRows.find(id).orElseThrow().getProcessingExpiresAt()).isEqualTo(originalDeadline);
    assertThat(traces.get(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertAvailable(9);

    jdbc.sql("UPDATE order_submit_trace SET processing_expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusSeconds(1))
        .param("id", id)
        .update();
    listener.onMessage(buildDeadLetter(id, eventId));
    listener.onMessage(buildDeadLetter(id, eventId));
    consumer.consume(id);
    assertThat(traces.get(10001, id).getFailureCode()).isEqualTo("QUEUE_TIMEOUT");
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("RELEASED");
    assertThat(orderCount(id)).isZero();
    assertThat(builds(id)).isEqualTo(1);
    assertAvailable(10);
  }

  @Test
  void deadLetterWithInvalidIdentityLeavesInventoryAndDatabaseErrorCanRetry() {
    String id = prepare();
    submissions.submit(10001, id);
    String eventId = buildEventId(id);
    jdbc.sql("UPDATE order_submit_trace SET processing_expires_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusSeconds(1))
        .param("id", id)
        .update();
    var listener = new OrderBuildDeadLetterConsumer(json, recovery);
    listener.onMessage(buildDeadLetter("无效购买编号", eventId));
    listener.onMessage(buildDeadLetter(UUID.randomUUID().toString(), eventId));
    listener.onMessage(buildDeadLetter(id, UUID.randomUUID().toString()));

    doThrow(new DataAccessResourceFailureException("模拟死信核对数据库失败"))
        .when(outbox).matchesBuild(eventId, id);
    assertThatThrownBy(() -> listener.onMessage(buildDeadLetter(id, eventId)))
        .isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(traces.get(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertThat(state(id)).isEqualTo("RESERVED");
    assertAvailable(9);
  }

  @Test
  void deadLetterForSuccessfulOrderOnlyFinishesInventoryConfirmation() {
    String id = prepare();
    submissions.submit(10001, id);
    doThrow(new IllegalStateException("模拟提交后确认失败"))
        .when(redis).finish(eq(sku), eq(id), eq(1), eq("CONFIRM"));
    consumer.consume(id);
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("CONFIRM_PENDING");
    reset(redis);

    var listener = new OrderBuildDeadLetterConsumer(json, recovery);
    listener.onMessage(buildDeadLetter(id, buildEventId(id)));
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("CONFIRMED");
    assertThat(orderCount(id)).isEqualTo(1);
    assertAvailable(9);
  }

  /** 构造 Broker 投递的原消息身份，直接验证消费入口与真实业务事务。 */
  private MessageExt buildDeadLetter(String traceId, String eventId) {
    var message = new MessageExt();
    message.setKeys(eventId);
    message.setBody(("{\"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8));
    return message;
  }

  private String buildEventId(String traceId) {
    return jdbc.sql("SELECT event_id FROM order_message_outbox WHERE trace_id=:id AND event_type='ORDER_BUILD'")
        .param("id", traceId).query(String.class).single();
  }

  @Test
  void cancelledOrderCanReturnAnAsYetUnconfirmedReservation() {
    String id = prepare();
    doThrow(new IllegalStateException("模拟提交后确认失败"))
        .when(redis)
        .finish(eq(sku), eq(id), eq(1), eq("CONFIRM"));
    submissions.submit(10001, id);
    consumer.consume(id);
    String order = traces.get(10001, id).getOrderId();
    assertThat(traces.get(10001, id).getStatus()).isEqualTo("SUCCESS");
    assertAvailable(9);
    lifecycle.cancel(10001, order);
    settlement.settleReturn(order, sku);
    recovery.reconcile(id);
    assertAvailable(10);
    assertThat(state(id)).isEqualTo("RETURNED");
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("RETURNED");
  }

  /** 实际延时消息入口关闭订单；数据库、Redis 和重复补偿均只返还一次。 */
  @Test
  void paymentTimeoutMessageReturnsSharedRedisReservationOnlyOnce() throws Exception {
    String id = prepare();
    submissions.submit(10001, id);
    consumer.consume(id);
    settlement.settleTrace(id);
    String order = traces.get(10001, id).getOrderId();
    String payload =
        jdbc.sql(
                "SELECT payload FROM order_message_outbox WHERE order_id=:id"
                    + " AND event_type='ORDER_PAYMENT_TIMEOUT'")
            .param("id", order)
            .query(String.class)
            .single();
    jdbc.sql("UPDATE customer_order SET payment_expires_at=:at WHERE id=:id")
        .param("at", LocalDateTime.now().minusSeconds(2))
        .param("id", order)
        .update();
    var timeoutConsumer =
        new com.bitmall.agent.order.mq.consumer.OrderPaymentTimeoutConsumer(
            tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
            lifecycle);
    timeoutConsumer.onMessage(payload);
    timeoutConsumer.onMessage(payload);
    settlement.settleReturn(order, sku);
    settlement.settleReturn(order, sku);
    assertAvailable(10);
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(state(id)).isEqualTo("RETURNED");
    assertThat(
            jdbc.sql("SELECT status FROM customer_order WHERE id=:id")
                .param("id", order)
                .query(String.class)
                .single())
        .isEqualTo("CLOSED");
  }

  @Test
  void concurrentSubmissionsCannotReserveMoreThanAvailable() throws Exception {
    jdbc.sql("UPDATE product_sku SET stock=1 WHERE id=:id").param("id", sku).update();
    var requests = new ArrayList<String>();
    for (int i = 0; i < 8; i++) requests.add(prepare());
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(8);
    try {
      var results = new ArrayList<Future<String>>();
      for (var id : requests)
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return submissions.submit(10001, id).getStatus();
                }));
      start.countDown();
      int accepted = 0;
      for (var result : results)
        if ("PROCESSING".equals(result.get(15, TimeUnit.SECONDS))) accepted++;
      assertThat(accepted).isEqualTo(1);
      assertAvailable(0);
      assertThat(stock()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void partiallyReservedCartIsReleasedAndNoBuildCommandIsWritten() {
    long second = ids.incrementAndGet();
    jdbc.sql(
            "INSERT INTO"
                + " product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)"
                + " SELECT :id,product_id,:code,spec,price,original_price,1,TRUE FROM product_sku"
                + " WHERE id=1003")
        .param("id", second)
        .param("code", "RESERVE-" + second)
        .update();
    // 先独占第二个 SKU 的最后一件，数据库仍有货，购物车试算可以通过。
    var secondQuote = checkout.checkoutQuote(10001, second, 1, 20001L, null);
    String other =
        traces
            .prepare(
                10001,
                new CreateOrderCommand(
                    "DIRECT", second, 1, 20001L, null, secondQuote.getPayAmount()),
                null)
            .getTraceId();
    submissions.submit(10001, other);
    jdbc.sql("UPDATE cart_item SET selected=FALSE WHERE user_id=10001").update();
    for (long item : new long[] {sku, second})
      jdbc.sql("INSERT INTO cart_item(user_id,sku_id,quantity,selected) VALUES(10001,:sku,1,TRUE)")
          .param("sku", item)
          .update();
    var quote = checkout.cartCheckoutQuote(10001, 20001L, null);
    String id =
        traces
            .prepare(
                10001,
                new CreateOrderCommand("CART", null, 1, 20001L, null, quote.getPayAmount()),
                null)
            .getTraceId();
    assertThat(submissions.submit(10001, id).getFailureCode()).isEqualTo("OUT_OF_STOCK");
    settlement.settleTrace(id);
    assertAvailable(10);
    assertThat(state(id)).isEqualTo("RELEASED");
    assertThat(builds(id)).isZero();
    assertThat(client.template().opsForValue().get(redis.availableKey(second))).isEqualTo("0");
    assertThat(client.template().opsForHash().get(redis.reservationKey(second, id), "state"))
        .isEqualTo("RELEASED");
    assertThatThrownBy(() -> redis.reserve(second, id, 1)).hasMessageContaining("已结束");
  }

  @Test
  void changedCartItemsCannotPassConfirmationMerelyBecauseAmountIsUnchanged() {
    jdbc.sql("UPDATE cart_item SET selected=FALSE WHERE user_id=10001").update();
    jdbc.sql("INSERT INTO cart_item(user_id,sku_id,quantity,selected) VALUES(10001,:sku,1,TRUE)")
        .param("sku", sku)
        .update();
    var quote = checkout.cartCheckoutQuote(10001, 20001L, null);
    var wrongItems = List.of(new com.bitmall.agent.order.model.PurchaseItem(sku + 9999, 1));
    var command =
        new CreateOrderCommand("CART", null, 1, 20001L, null, quote.getPayAmount(), wrongItems);
    String id = controller.issue().get("traceId");
    assertThatThrownBy(() -> submissions.submit(10001, id, command))
        .hasMessageContaining("购买明细已经变化");
    assertThat(traceRows.find(id)).isEmpty();
    assertThat(builds(id)).isZero();
  }

  @Test
  void redisReleaseThenDatabaseProgressRollbackCanRetryWithoutDoubleReturn() {
    String id = prepare();
    inventory.reserve(10001, id);
    doThrow(new DataAccessResourceFailureException("模拟释放进度提交失败"))
        .when(inventoryRows)
        .stage(argThat(args -> id.equals(args.get("id")) && "RELEASED".equals(args.get("state"))));
    jdbc.sql("UPDATE order_submit_trace SET next_check_at=:at WHERE trace_id=:id")
        .param("at", LocalDateTime.now().minusMinutes(1))
        .param("id", id)
        .update();
    recovery.reconcile(id);
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("RELEASE_PENDING");
    reset(inventoryRows);
    settlement.settleTrace(id);
    settlement.settleTrace(id);
    assertAvailable(10);
    assertThat(state(id)).isEqualTo("RELEASED");
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("RELEASED");
  }

  @Test
  void redisConfirmThenDatabaseProgressRollbackCanRetryWithoutChangingStock() {
    String id = prepare();
    submissions.submit(10001, id);
    doThrow(new DataAccessResourceFailureException("模拟确认进度提交失败"))
        .when(inventoryRows)
        .stage(argThat(args -> id.equals(args.get("id")) && "CONFIRMED".equals(args.get("state"))));
    consumer.consume(id);
    verify(inventoryRows, timeout(3000).atLeastOnce())
        .stage(argThat(args -> id.equals(args.get("id")) && "CONFIRMED".equals(args.get("state"))));
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("CONFIRM_PENDING");
    reset(inventoryRows);
    recovery.reconcile(id);
    settlement.settleTrace(id);
    assertAvailable(9);
    assertThat(state(id)).isEqualTo("CONFIRMED");
    assertThat(traceRows.find(id).orElseThrow().getReserveStatus()).isEqualTo("CONFIRMED");
    assertThat(orderCount(id)).isEqualTo(1);
  }

  /** 固定异步故障时不预扣，恢复后可用同一凭证，已受理的重试仍读取原结果。 */
  @Test
  void unavailableBrokerRejectsBeforeStockReservationAndAllowsSameTraceToRetry() {
    String id = prepare();
    when(broker.isHealthy()).thenReturn(false);
    assertThatThrownBy(() -> submissions.submit(10001, id))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    var trace = traceRows.find(id).orElseThrow();
    assertThat(trace.getStatus()).isEqualTo("INIT");
    assertThat(trace.getReserveStatus()).isEqualTo("NONE");
    assertThat(state(id)).isNull();
    assertThat(client.template().opsForValue().get(redis.availableKey(sku))).isNull();
    assertThat(stock()).isEqualTo(10);
    assertThat(jdbc.sql("SELECT COUNT(*) FROM order_message_outbox WHERE trace_id=:id")
        .param("id", id).query(Long.class).single()).isZero();

    when(broker.isHealthy()).thenReturn(true);
    assertThat(submissions.submit(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertAvailable(9);
    assertThat(outbox.findById(buildEventId(id))).isPresent();
    when(broker.isHealthy()).thenReturn(false);
    assertThat(submissions.submit(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertAvailable(9);
  }

  private CreateOrderCommand command() {
    var q = checkout.checkoutQuote(10001, sku, 1, 20001L, null);
    return new CreateOrderCommand("DIRECT", sku, 1, 20001L, null, q.getPayAmount());
  }

  private String prepare() {
    return traces.prepare(10001, command(), null).getTraceId();
  }

  private void assertAvailable(int available) {
    assertThat(client.template().opsForValue().get(redis.availableKey(sku)))
        .isEqualTo(String.valueOf(available));
  }

  private String legacyPendingKey() {
    return namespace + "inventory:v1:{" + sku + "}:pending";
  }

  private String state(String id) {
    return (String) client.template().opsForHash().get(redis.reservationKey(sku, id), "state");
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

  private long orderCount(String id) {
    return jdbc.sql("SELECT COUNT(*) FROM customer_order WHERE submit_trace_id=:id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private long builds(String id) {
    return jdbc.sql(
            "SELECT COUNT(*) FROM order_message_outbox WHERE trace_id=:id AND"
                + " event_type='ORDER_BUILD'")
        .param("id", id)
        .query(Long.class)
        .single();
  }
}
