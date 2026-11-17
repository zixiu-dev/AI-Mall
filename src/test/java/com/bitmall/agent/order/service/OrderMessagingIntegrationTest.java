package com.bitmall.agent.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.dao.entity.OrderMessageDO;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.mq.consumer.OrderEventConsumer;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;
import com.bitmall.agent.order.mq.producer.OrderEventProducer;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 使用真实 H2 事务验证订单与消息同进同退、发送恢复和重复消费。网络发送用替身，不访问共享开发集群。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:order-messaging;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class OrderMessagingIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  com.bitmall.agent.order.service.OrderTraceService orderTraces;

  /** 实际订单业务入口。 */
  @Autowired OrderService orders;

  /** 使用数据库当前价格生成有效建单参数。 */
  @Autowired CheckoutService checkout;

  /** 实际本地消息数据访问组件。 */
  @Autowired OrderMessageRepository mapper;

  /** 在外层事务中验证出错时业务和消息一起回滚。 */
  @Autowired PlatformTransactionManager transactions;

  /** 使用实际事务代理记录消费者回执。 */
  @Autowired OrderEventReceiptService receipts;

  /** 与生产应用相同的 JSON 配置。 */
  @Autowired ObjectMapper json;

  /** 断言数据库中的订单、消息和回执。 */
  @Autowired JdbcClient jdbc;

  /** 检查关闭 MQ 时没有创建任何客户端。 */
  @Autowired ApplicationContext context;

  @Autowired com.bitmall.agent.order.monitor.OrderMetrics metrics;

  /** 每个测试独立处理本地消息，避免上一例的未发送数据影响扫描。 */
  @BeforeEach
  void clearMessages() {
    jdbc.sql("DELETE FROM order_message_outbox").update();
    jdbc.sql("DELETE FROM order_event_receipt").update();
  }

  /** 创建与取消产生两个事件和一条超时命令；重复取消不增加消息，正文不包含收货地址。 */
  @Test
  void orderChangesCommitWithTheirMessages() throws Exception {
    Object beforeCreated = metrics.snapshot().get("ordersCreatedTotal");
    String id = createOrder();
    orders.cancelOrder(10001, id);
    assertThat(orders.cancelOrder(10001, id).get("status")).isEqualTo("CANCELLED");
    assertThat(
            ((Number) metrics.snapshot().get("ordersCreatedTotal")).doubleValue()
                - ((Number) beforeCreated).doubleValue())
        .isEqualTo(1.0);
    assertThat(count("order_message_outbox")).isEqualTo(3);
    var messages = mapper.findReady(10);
    assertThat(messages)
        .extracting(OrderMessageDO::getEventType)
        .containsExactlyInAnyOrder(
            OrderEventMessage.CREATED,
            OrderEventMessage.CANCELLED,
            OrderPaymentTimeoutMessage.TYPE);
    for (OrderMessageDO row : messages) {
      if (OrderPaymentTimeoutMessage.TYPE.equals(row.getEventType())) continue;
      var event = json.readValue(row.getPayload(), OrderEventMessage.class);
      assertThat(event.getOrderId()).isEqualTo(id);
      assertThat(event.getUserId()).isEqualTo(10001);
      assertThat(event.getEventId()).isEqualTo(row.getEventId());
      assertThat(json.readTree(row.getPayload()).properties())
          .extracting(Map.Entry::getKey)
          .containsExactlyInAnyOrder(
              "eventId", "eventType", "orderId", "userId", "payAmount", "occurredAt");
    }
  }

  /** 模拟 Agent 确认执行的外层事务失败，验证订单、库存和本地消息一起回滚。 */
  @Test
  void outerTransactionRollbackDoesNotLeaveMessagesOrOrders() {
    Object committedBefore = metrics.snapshot().get("ordersCreatedTotal");
    int before = count("customer_order");
    int stock =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          createOrder();
                          assertThat(count("order_message_outbox")).isEqualTo(2);
                          throw new IllegalStateException("确认执行后续步骤失败");
                        }))
        .hasMessageContaining("后续步骤失败");
    assertThat(count("customer_order")).isEqualTo(before);
    assertThat(metrics.snapshot().get("ordersCreatedTotal")).isEqualTo(committedBefore);
    assertThat(count("order_message_outbox")).isZero();
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(stock);
  }

  /** 网络失败后事件仍在本地，下次使用相同编号和原文发送，成功才更新为 SENT。 */
  @Test
  void failedSendIsRetriedWithoutChangingTheEvent() {
    createOrder();
    OrderEventProducer producer = mock(OrderEventProducer.class);
    when(producer.send(any()))
        .thenThrow(new IllegalStateException("模拟 Broker 不在线"))
        .thenReturn("broker-id-1");
    var relay = relay(producer);
    String eventId = createdMessage().getEventId();
    relay.relayEvent(eventId);
    assertThat(status()).isEqualTo("PENDING");
    assertThat(
            jdbc.sql("SELECT last_error FROM order_message_outbox WHERE event_type='ORDER_CREATED'")
                .query(String.class)
                .single())
        .contains("不在线");
    relay.relayEvent(eventId);
    verify(producer, times(1)).send(any());
    jdbc.sql(
            "UPDATE order_message_outbox SET next_attempt_at = TIMESTAMP '2000-01-01"
                + " 00:00:00'")
        .update();
    relay.relayEvent(eventId);
    assertThat(status()).isEqualTo("SENT");
    ArgumentCaptor<OrderMessageDO> captured = ArgumentCaptor.forClass(OrderMessageDO.class);
    verify(producer, times(2)).send(captured.capture());
    assertThat(captured.getAllValues())
        .extracting(OrderMessageDO::getEventId)
        .containsOnly(captured.getValue().getEventId());
    assertThat(captured.getAllValues())
        .extracting(OrderMessageDO::getPayload)
        .containsOnly(captured.getValue().getPayload());
  }

  /** 一个发送者占用时另一个不能抢到；租约过期可以接手，旧令牌不能标记新尝试成功。 */
  @Test
  void expiredLeasesCanBeRecoveredWithoutAcceptingOldAcknowledgements() {
    createOrder();
    String id = createdMessage().getEventId();
    assertThat(mapper.claim(id, "first", LocalDateTime.now().plusMinutes(1))).isTrue();
    assertThat(mapper.claim(id, "second", LocalDateTime.now().plusMinutes(1))).isFalse();
    jdbc.sql("UPDATE order_message_outbox SET locked_until = TIMESTAMP '2000-01-01 00:00:00'")
        .update();
    assertThat(mapper.findReady(10)).extracting(OrderMessageDO::getEventId).contains(id);
    assertThat(mapper.claim(id, "second", LocalDateTime.now().plusMinutes(1))).isTrue();
    assertThat(mapper.markSent(id, "first", "old")).isFalse();
    assertThat(mapper.markSent(id, "second", "new")).isTrue();
    assertThat(mapper.findReady(10)).extracting(OrderMessageDO::getEventId).doesNotContain(id);
  }

  /** 同一事件重复消费只保留一条回执；异常格式不确认成功，也不能写入回执。 */
  @Test
  void consumptionIsIdempotentAndMalformedMessagesFail() {
    createOrder();
    String payload = createdMessage().getPayload();
    var consumer = new OrderEventConsumer(json, receipts);
    consumer.onMessage(payload);
    consumer.onMessage(payload);
    assertThat(count("order_event_receipt")).isEqualTo(1);
    assertThatThrownBy(() -> consumer.onMessage("{}")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> consumer.onMessage("null"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(count("order_event_receipt")).isEqualTo(1);
  }

  /** 审计回执写库失败必须传播给消费框架，以便重试，不能吞掉错误后丢消息。 */
  @Test
  void consumerDoesNotAcknowledgeDatabaseFailures() {
    createOrder();
    var failing = mock(OrderEventReceiptService.class);
    doThrow(new IllegalStateException("模拟数据库故障")).when(failing).record(any());
    var consumer = new OrderEventConsumer(json, failing);
    assertThatThrownBy(() -> consumer.onMessage(createdMessage().getPayload()))
        .hasMessageContaining("数据库故障");
    assertThat(count("order_event_receipt")).isZero();
  }

  /** 校验生产者使用原始 JSON、Topic:Tag 和业务 Key，并拒绝 Broker 的非成功回执。 */
  @Test
  void producerRequiresBrokerAcknowledgementAndKeepsTheMessageKey() {
    var template = mock(RocketMQTemplate.class);
    var result = new SendResult();
    result.setSendStatus(SendStatus.SEND_OK);
    result.setMsgId("broker-id");
    when(template.syncSend(anyString(), any(Message.class))).thenReturn(result);
    var producer = new OrderEventProducer(template);
    var message =
        new OrderMessageDO(
            UUID.randomUUID().toString(),
            "test_topic",
            OrderEventMessage.CREATED,
            "{\"test\":true}",
            0);
    assertThat(producer.send(message)).isEqualTo("broker-id");
    ArgumentCaptor<Message<?>> sent = ArgumentCaptor.forClass(Message.class);
    verify(template).syncSend(eq("test_topic:ORDER_CREATED"), sent.capture());
    assertThat(sent.getValue().getPayload()).isEqualTo(message.getPayload());
    assertThat(sent.getValue().getHeaders().get("KEYS")).isEqualTo(message.getEventId());
    result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
    assertThatThrownBy(() -> producer.send(message)).hasMessageContaining("未确认");
  }

  /** 即使配置了远程 NameServer，关闭开关也不会创建 MQ 客户端、消费者或调度发送器。 */
  @Test
  void disabledMessagingDoesNotConnectToRocketMq() {
    assertThat(context.getBeansOfType(RocketMQTemplate.class)).isEmpty();
    assertThat(context.getBeansOfType(OrderEventConsumer.class)).isEmpty();
    assertThat(context.getBeansOfType(OrderMessageRelayService.class)).isEmpty();
  }

  /** 使用真实报价创建一笔立即购买订单。 */
  private String createOrder() {
    var quote = checkout.checkoutQuote(10001, 1003, 1, 20001L);
    return (String)
        orders
            .createOrder(
                10001,
                orderTraces
                    .prepare(
                        10001,
                        new CreateOrderCommand(
                            "DIRECT", 1003L, 1, 20001L, null, quote.getPayAmount()),
                        null)
                    .getTraceId())
            .get("orderId");
  }

  /** 手动执行后台服务，使测试无需等待定时器，也不访问外部 MQ。 */
  private OrderMessageRelayService relay(OrderEventProducer producer) {
    return new OrderMessageRelayService(
        mapper, producer, new OrderMessagingProperties(true, "test_topic", 20, 60, 10));
  }

  /** 读取当前唯一待测消息的状态。 */
  private String status() {
    return jdbc.sql("SELECT status FROM order_message_outbox WHERE event_type='ORDER_CREATED'")
        .query(String.class)
        .single();
  }

  /** 按业务类型选择，避免依赖同时创建的定时命令与事件的随机编号顺序。 */
  private OrderMessageDO createdMessage() {
    return mapper.findReady(100).stream()
        .filter(m -> OrderEventMessage.CREATED.equals(m.getEventType()))
        .findFirst()
        .orElseThrow();
  }

  /** 统计测试中固定的几张表，表名只由测试代码提供。 */
  private int count(String table) {
    return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
  }
}
