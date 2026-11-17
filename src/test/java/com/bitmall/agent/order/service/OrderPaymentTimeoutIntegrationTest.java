package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.entity.OrderMessageDO;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.mq.consumer.OrderPaymentTimeoutConsumer;
import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;
import com.bitmall.agent.order.mq.producer.OrderEventProducer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/** 真实数据库事务验证超时命令、幂等关单与支付竞争；MQ 发送仅在协议适配测试里使用替身。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:payment-timeout;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.catalog-cache.enabled=false"
    })
@ActiveProfiles("demo")
class OrderPaymentTimeoutIntegrationTest {
  @Autowired OrderService orders;
  @Autowired OrderTraceService traces;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired CheckoutService checkout;
  @Autowired OrderMessageRepository messages;
  @Autowired OrderProcessingProperties properties;
  @Autowired ObjectMapper json;
  @Autowired JdbcClient jdbc;

  /** 禁用后台扫描，证明本组关单由消息入口触发。 */
  @MockitoBean OrderRecoveryScheduler scheduler;

  private static final AtomicLong IDS = new AtomicLong(950000);
  private long sku;

  @BeforeEach
  void prepareIsolatedSku() {
    sku = IDS.incrementAndGet();
    jdbc.sql(
            "INSERT INTO"
                + " product_sku(id,product_id,sku_code,spec,price,original_price,stock,enabled)"
                + " SELECT :id,product_id,:code,spec,price,original_price,10,TRUE FROM product_sku"
                + " WHERE id=1003")
        .param("id", sku)
        .param("code", "PAYMENT-TIMEOUT-" + sku)
        .update();
    jdbc.sql(
            "UPDATE user_coupon SET status='AVAILABLE',used_order_id=NULL,used_at=NULL"
                + " WHERE user_id=10001 AND coupon_id=3001")
        .update();
  }

  @Test
  void newOrderPersistsOneTimerAtOriginalFifteenMinuteDeadline() throws Exception {
    long before = System.currentTimeMillis();
    String orderId = createOrder(null);
    var order = orders.order(10001, orderId);
    long deadline =
        order.getPaymentExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    assertThat(properties.getPaymentTtlSeconds()).isEqualTo(900);
    assertThat(deadline).isBetween(before + 900_000, System.currentTimeMillis() + 900_000);
    var stored = timeout(orderId);
    var command = json.readValue(stored.getPayload(), OrderPaymentTimeoutMessage.class);
    assertThat(stored.getDeliverAtEpochMillis()).isEqualTo(deadline);
    assertThat(command.getPaymentExpiresAtEpochMillis()).isEqualTo(deadline);
    assertThat(command.getOrderId()).isEqualTo(orderId);
    assertThat(command.getEventId()).isEqualTo(stored.getEventId());
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM order_message_outbox WHERE order_id=:id"
                        + " AND event_type='ORDER_PAYMENT_TIMEOUT'")
                .param("id", orderId)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void prematureOrForgedDeadlineCannotCloseUnexpiredOrder() throws Exception {
    String orderId = createOrder(null);
    var forged = new OrderPaymentTimeoutMessage(UUID.randomUUID().toString(), orderId, 10001, 1L);
    assertThatThrownBy(() -> consumer().onMessage(json.writeValueAsString(forged)))
        .hasMessageContaining("尚未到");
    assertThat(orders.order(10001, orderId).getStatus()).isEqualTo("PENDING_PAYMENT");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isEqualTo(1);
  }

  @Test
  void expiredMessageClosesOnceAndReleasesCouponAndDatabaseStock() {
    long coupon =
        jdbc.sql("SELECT id FROM user_coupon WHERE user_id=10001 AND coupon_id=3001")
            .query(Long.class)
            .single();
    String id = createOrder(coupon);
    String payload = timeout(id).getPayload();
    expire(id);
    consumer().onMessage(payload);
    consumer().onMessage(payload);
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("CLOSED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
    assertThat(
            jdbc.sql("SELECT status FROM user_coupon WHERE id=:id")
                .param("id", coupon)
                .query(String.class)
                .single())
        .isEqualTo("AVAILABLE");
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM order_message_outbox WHERE order_id=:id"
                        + " AND event_type='ORDER_CANCELLED'")
                .param("id", id)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThatThrownBy(() -> lifecycle.payDemo(10001, id)).hasMessageContaining("不能支付");
  }

  @Test
  void paidOrManuallyCancelledOrderIsNeverClosedAgain() {
    String paid = createOrder(null);
    String paidMessage = timeout(paid).getPayload();
    lifecycle.payDemo(10001, paid);
    expire(paid);
    consumer().onMessage(paidMessage);
    assertThat(orders.order(10001, paid).getStatus()).isEqualTo("PAID");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isZero();
    String cancelled = createOrder(null);
    String cancelledMessage = timeout(cancelled).getPayload();
    orders.cancelOrder(10001, cancelled);
    consumer().onMessage(cancelledMessage);
    assertThat(orders.order(10001, cancelled).getStatus()).isEqualTo("CANCELLED");
    assertThat(stock()).isEqualTo(9);
    assertThat(locked()).isZero();
  }

  @Test
  void paymentRacingWithExpiredMessageLeavesOneClosedInventoryOutcome() throws Exception {
    String id = createOrder(null);
    String payload = timeout(id).getPayload();
    expire(id);
    var start = new CountDownLatch(1);
    try (var executor = new AutoCloseableExecutor()) {
      var jobs =
          List.of(
              executor.pool.submit(
                  () -> {
                    await(start);
                    lifecycle.payDemo(10001, id);
                  }),
              executor.pool.submit(
                  () -> {
                    await(start);
                    consumer().onMessage(payload);
                  }));
      start.countDown();
      for (var job : jobs) {
        try {
          job.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
          assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        }
      }
    }
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("CLOSED");
    assertThat(stock()).isEqualTo(10);
    assertThat(locked()).isZero();
  }

  @Test
  void producerUsesStableAbsoluteDeliveryTimeAndImmediatelySendsOverdueRetries() {
    var template = mock(RocketMQTemplate.class);
    var result = new SendResult();
    result.setSendStatus(SendStatus.SEND_OK);
    result.setMsgId("timer-id");
    when(template.syncSendDeliverTimeMills(anyString(), any(Message.class), anyLong()))
        .thenReturn(result);
    when(template.syncSend(anyString(), any(Message.class))).thenReturn(result);
    var producer = new OrderEventProducer(template);
    long deadline = System.currentTimeMillis() + 900_000;
    var row =
        new OrderMessageDO(
            "timer-event", "test-topic", OrderPaymentTimeoutMessage.TYPE, "{}", 0, deadline);
    producer.send(row);
    producer.send(row);
    var sent = ArgumentCaptor.forClass(Message.class);
    verify(template, times(2))
        .syncSendDeliverTimeMills(
            eq("test-topic:ORDER_PAYMENT_TIMEOUT"), sent.capture(), eq(deadline));
    assertThat(sent.getValue().getHeaders().get("KEYS")).isEqualTo("timer-event");
    producer.send(
        new OrderMessageDO(
            "late-event", "test-topic", OrderPaymentTimeoutMessage.TYPE, "{}", 2, 1L));
    verify(template).syncSend(eq("test-topic:ORDER_PAYMENT_TIMEOUT"), any(Message.class));
    result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
    assertThatThrownBy(() -> producer.send(row)).hasMessageContaining("未确认");
    assertThatThrownBy(
            () ->
                producer.send(
                    new OrderMessageDO(
                        "bad", "test-topic", OrderPaymentTimeoutMessage.TYPE, "{}", 0)))
        .hasMessageContaining("原始投递时刻");
  }

  @Test
  void malformedCommandsAndDatabaseFailuresAreNotAcknowledged() throws Exception {
    assertThatThrownBy(() -> consumer().onMessage("{}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> consumer().onMessage("null"))
        .isInstanceOf(IllegalArgumentException.class);
    var failing = mock(OrderLifecycleService.class);
    when(failing.closeExpired(anyLong(), anyString()))
        .thenThrow(new IllegalStateException("数据库故障"));
    var command = new OrderPaymentTimeoutMessage("event", "order", 10001, 1);
    assertThatThrownBy(
            () ->
                new OrderPaymentTimeoutConsumer(json, failing)
                    .onMessage(json.writeValueAsString(command)))
        .hasMessageContaining("数据库故障");
  }

  private OrderPaymentTimeoutConsumer consumer() {
    return new OrderPaymentTimeoutConsumer(json, lifecycle);
  }

  private String createOrder(Long coupon) {
    var quote = checkout.checkoutQuote(10001, sku, 1, 20001L, coupon);
    var trace =
        traces.prepare(
            10001,
            new CreateOrderCommand("DIRECT", sku, 1, 20001L, coupon, quote.getPayAmount()),
            null);
    return (String) orders.createOrder(10001, trace.getTraceId()).get("orderId");
  }

  private OrderMessageDO timeout(String orderId) {
    String event =
        jdbc.sql(
                "SELECT event_id FROM order_message_outbox WHERE order_id=:id"
                    + " AND event_type='ORDER_PAYMENT_TIMEOUT'")
            .param("id", orderId)
            .query(String.class)
            .single();
    return messages.findById(event).orElseThrow();
  }

  private void expire(String id) {
    jdbc.sql("UPDATE customer_order SET payment_expires_at=:at WHERE id=:id")
        .param("at", LocalDateTime.now().minusSeconds(2))
        .param("id", id)
        .update();
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

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  /** Java 17 的线程池尚未实现 AutoCloseable，测试退出时确保清理并发任务。 */
  private static final class AutoCloseableExecutor implements AutoCloseable {
    final ExecutorService pool = Executors.newFixedThreadPool(2);

    public void close() {
      pool.shutdownNow();
    }
  }
}
