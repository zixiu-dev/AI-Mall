package com.bitmall.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bitmall.agent.agent.dao.repository.ConversationRepository;
import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import com.bitmall.agent.agent.dao.repository.AgentExecutionDetailRepository;
import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.coupon.service.CouponService;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.order.service.OrderSubmissionService;
import com.bitmall.agent.order.service.OrderTraceService;
import com.bitmall.agent.product.service.ProductService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Runs against an explicitly supplied disposable MySQL schema. */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
class MySQLMigrationIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  com.bitmall.agent.order.service.OrderTraceService orderTraces;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.bitmall.agent.order.config.OrderProcessingProperties processing;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.bitmall.agent.order.config.OrderMessagingProperties messaging;

  @Autowired com.bitmall.agent.order.service.OrderBuildCommandService buildCommands;
  @Autowired com.bitmall.agent.order.service.OrderRecoveryService recovery;
  @Autowired DataSource dataSource;
  @Autowired JdbcClient jdbc;
  @Autowired ProductService products;
  @Autowired KnowledgeService knowledge;
  @Autowired CouponService coupons;
  @Autowired CheckoutService checkout;
  @Autowired OrderService orders;
  @Autowired com.bitmall.agent.order.service.OrderLifecycleService lifecycle;
  @Autowired OrderTraceService traces;
  @Autowired OrderSubmissionService submissions;
  @Autowired ConversationRepository conversations;
  @Autowired AgentExecutionDetailRepository executionDetails;
  @Autowired com.bitmall.agent.agent.dao.mapper.PendingActionMapper pending;

  @Test
  void migrationsCreateForeignKeysAndRemoveLegacyActivityModel() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
    }
    assertThat(
            jdbc.sql(
                    """
                    SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA=DATABASE()
                    """)
                .query(Integer.class)
                .single())
        .isGreaterThan(20);
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
                        + " AND table_name='order_sale_activity'")
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("""
                SELECT type FROM flyway_schema_history
                WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank
                """)
                .query(String.class)
                .list())
        .containsExactly("SQL_BASELINE", "SQL");
    assertThat(jdbc.sql("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
          AND table_comment = ''
        """).query(Integer.class).single()).isZero();
    assertThat(jdbc.sql("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
          AND column_comment = ''
        """).query(Integer.class).single()).isZero();
    assertThat(products.product(101L).getSkus()).isNotEmpty();
    assertThat(knowledge.search("优惠券")).isNotEmpty();
  }

  @Autowired com.bitmall.agent.agent.service.conversation.ConversationService conversationService;

  @Test
  void resumableAnswerStoresQuestionAndUpdatesSameMessageInMySql() {
    String conversationId = java.util.UUID.randomUUID().toString();
    String requestKey = "10001:" + java.util.UUID.randomUUID();
    var accepted = conversationService.createAnswer(10001L, conversationId, true, "查订单", requestKey);
    long messageId = accepted.getMessage().getId();
    assertThat(accepted.getMessage().getGenerationStatus()).isEqualTo("GENERATING");
    assertThat(accepted.getMessage().getReplyToMessageId()).isPositive();
    assertThat(conversationService.answerByRequest(10001L, requestKey)).isPresent();

    conversationService.finishAnswer(conversationId, messageId, "COMPLETED", "完整回复", Map.of());
    assertThat(conversationService.answer(10001L, messageId).getMessage().getContent()).isEqualTo("完整回复");
    assertThat(conversations.recentMessages(conversationId, 10)).hasSize(2);
  }

  @Test
  void executionDetailsBatchQueryAndRetentionWorkInMySql() {
    var accepted = conversationService.createAnswer(10001L, UUID.randomUUID().toString(), true,
        "查询明细", "10001:" + UUID.randomUUID());
    long turnId = accepted.getMessage().getId();
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    var expired = AgentExecutionDetailDO.builder().turnId(turnId).sequenceNo(1).stepNo(1).attemptNo(1)
        .candidate("primary").model("test").status("FAILED").errorCode("HTTP_503")
        .startedAt(now.minusDays(8)).durationMs(3).build();
    var success = AgentExecutionDetailDO.builder().turnId(turnId).sequenceNo(2).stepNo(1).attemptNo(2)
        .candidate("bailian").model("qwen-plus").status("SUCCESS")
        .startedAt(now).durationMs(8).inputTokens(7).outputTokens(2).build();
    var tool = AgentExecutionDetailDO.builder().turnId(turnId).sequenceNo(3).stepNo(1)
        .itemType("TOOL_CALL").modelCallSequence(2).toolName("product_detail").executionCount(2)
        .status("SUCCESS").startedAt(now).durationMs(9).build();
    var pastQueue = AgentExecutionDetailDO.builder().turnId(turnId).sequenceNo(4)
        .itemType("QUEUE_WAIT").status("SUCCESS").startedAt(now.minusDays(8)).durationMs(1).build();
    executionDetails.writeModelCalls(List.of(expired, success));
    executionDetails.writeItems(List.of(tool, pastQueue));
    assertThat(executionDetails.find(turnId, now.minusDays(7)))
        .extracting(AgentExecutionDetailDO::getSequenceNo).containsExactly(2, 3);
    executionDetails.prune(now.minusDays(7), 1);
    var retained = executionDetails.find(turnId, now.minusDays(10));
    assertThat(retained).hasSize(2);
    assertThat(retained.get(0).getInputTokens()).isEqualTo(7);
    assertThat(retained.get(1).getModelCallSequence()).isEqualTo(2);
    assertThat(retained.get(1).getExecutionCount()).isEqualTo(2);
    assertThat(conversationService.answer(10001L, turnId).getMessage().getId()).isEqualTo(turnId);
  }

  @Test
  void couponOrderCancellationAndGeneratedMessageIdsUseMySQL() {
    int before =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1001").query(Integer.class).single();
    long userCoupon = ((Number) coupons.claimCoupon(10001L, 3002L).get("userCouponId")).longValue();
    var quote = checkout.checkoutQuote(10001L, 1001L, 1, null, userCoupon);
    var result =
        orders.createOrder(
            10001L,
            orderTraces
                .prepare(
                    10001L,
                    new CreateOrderCommand(
                        "DIRECT", 1001L, 1, null, userCoupon, quote.getPayAmount()),
                    null)
                .getTraceId());
    String id = result.get("orderId").toString();
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1001").query(Integer.class).single())
        .isEqualTo(before - 1);
    assertThat(
            jdbc.sql("SELECT status FROM user_coupon WHERE id=:id")
                .param("id", userCoupon)
                .query(String.class)
                .single())
        .isEqualTo("LOCKED");
    orders.cancelOrder(10001L, id);
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1001").query(Integer.class).single())
        .isEqualTo(before);
    assertThat(
            jdbc.sql("SELECT status FROM user_coupon WHERE id=:id")
                .param("id", userCoupon)
                .query(String.class)
                .single())
        .isEqualTo("AVAILABLE");
    String session = UUID.randomUUID().toString();
    conversations.insert(session, 10001L, "MySQL verification");
    long message = conversations.addMessage(session, "user", "测试".repeat(40000), Map.of());
    assertThat(message).isPositive();
    assertThat(
            jdbc.sql("SELECT CHAR_LENGTH(content) FROM chat_message WHERE id=:id")
                .param("id", message)
                .query(Integer.class)
                .single())
        .isEqualTo(80000);
  }

  @Test
  @Transactional
  void duplicateConfirmationKeepsOriginalArguments() {
    String session = UUID.randomUUID().toString();
    conversations.insert(session, 10001L, "Confirmation retry");
    Map<String, Object> first = new java.util.HashMap<>();
    first.put("id", UUID.randomUUID().toString());
    first.put("key", UUID.randomUUID().toString());
    first.put("userId", 10001L);
    first.put("conversationId", session);
    first.put("toolName", "order_create");
    first.put("arguments", "{\"skuId\":1001}");
    first.put("hash", "a".repeat(64));
    first.put("summary", "Original confirmation");
    pending.insertSql(first);
    Map<String, Object> retry = new java.util.HashMap<>(first);
    retry.put("id", UUID.randomUUID().toString());
    retry.put("arguments", "{\"skuId\":1002}");
    pending.insertSql(retry);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM pending_action WHERE idempotency_key=:key")
                .param("key", first.get("key"))
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT arguments FROM pending_action WHERE idempotency_key=:key")
                .param("key", first.get("key"))
                .query(String.class)
                .single())
        .isEqualTo("{\"skuId\":1001}");
  }

  @Test
  void concurrentRetriesCreateOneOrderForTheSameTrace() throws Exception {
    var quote = checkout.checkoutQuote(10001L, 1003L, 1, null, null);
    var command = new CreateOrderCommand("DIRECT", 1003L, 1, null, null, quote.getPayAmount());
    String trace = traces.prepare(10001L, command, null).getTraceId();
    var executor = Executors.newFixedThreadPool(8);
    try {
      var tasks =
          IntStream.range(0, 8)
              .<Callable<String>>mapToObj(i -> () -> submissions.submit(10001L, trace).getOrderId())
              .toList();
      var futures = executor.invokeAll(tasks);
      String id = futures.get(0).get();
      for (var future : futures) assertThat(future.get()).isEqualTo(id);
      assertThat(
              jdbc.sql("SELECT COUNT(*) FROM customer_order WHERE submit_trace_id=:id")
                  .param("id", trace)
                  .query(Integer.class)
                  .single())
          .isEqualTo(1);
      orders.cancelOrder(10001L, id);
    } finally {
      executor.shutdownNow();
    }
  }

  /** 实际 MySQL 验证支付不重复扣可售库存，到期关闭只释放一次。 */
  @Test
  void paymentAndTimeoutUseRealMySQLInventoryLocks() {
    int before =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single();
    var quote = checkout.checkoutQuote(10001, 1003, 1, null);
    var command = new CreateOrderCommand("DIRECT", 1003L, 1, null, null, quote.getPayAmount());
    String paid =
        submissions.submit(10001, traces.prepare(10001, command, null).getTraceId()).getOrderId();
    lifecycle.payDemo(10001, paid);
    lifecycle.payDemo(10001, paid);
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(before - 1);
    assertThat(
            jdbc.sql("SELECT locked_stock FROM product_sku WHERE id=1003")
                .query(Integer.class)
                .single())
        .isZero();
    String expired =
        submissions.submit(10001, traces.prepare(10001, command, null).getTraceId()).getOrderId();
    jdbc.sql("UPDATE customer_order SET payment_expires_at=:at WHERE id=:id")
        .param("at", java.time.LocalDateTime.now().minusMinutes(1))
        .param("id", expired)
        .update();
    lifecycle.closeExpired(10001, expired);
    lifecycle.closeExpired(10001, expired);
    assertThat(orders.order(10001, expired).getStatus()).isEqualTo("CLOSED");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(before - 1);
    assertThat(
            jdbc.sql("SELECT locked_stock FROM product_sku WHERE id=1003")
                .query(Integer.class)
                .single())
        .isZero();
  }

  /** 实际 MySQL 验证排队受理、共享建单事务及未发送超时命令的 CANCELLED 状态。 */
  @Test
  void queuedBuildAndTimeoutUseRealMySQL() {
    org.mockito.Mockito.doReturn("ASYNC").when(processing).getSubmissionMode();
    org.mockito.Mockito.doReturn(true).when(messaging).isEnabled();
    int before =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single();
    var quote = checkout.checkoutQuote(10001, 1003, 1, null);
    var command = new CreateOrderCommand("DIRECT", 1003L, 1, null, null, quote.getPayAmount());
    String id = traces.prepare(10001, command, null).getTraceId();
    assertThat(submissions.submit(10001, id).getStatus()).isEqualTo("PROCESSING");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(before);
    buildCommands.consume(id);
    buildCommands.consume(id);
    var built = traces.get(10001, id);
    assertThat(built.getStatus()).isEqualTo("SUCCESS");
    assertThat(orders.order(10001, built.getOrderId()).getStatus()).isEqualTo("PENDING_PAYMENT");
    orders.cancelOrder(10001, built.getOrderId());
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(before);
    String timeout = traces.prepare(10001, command, null).getTraceId();
    submissions.submit(10001, timeout);
    jdbc.sql("UPDATE order_submit_trace SET processing_expires_at=:at WHERE trace_id=:id")
        .param("at", java.time.LocalDateTime.now().minusMinutes(1))
        .param("id", timeout)
        .update();
    recovery.reconcile(timeout);
    assertThat(traces.get(10001, timeout).getFailureCode()).isEqualTo("QUEUE_TIMEOUT");
    assertThat(
            jdbc.sql(
                    "SELECT status FROM order_message_outbox WHERE trace_id=:id AND"
                        + " event_type='ORDER_BUILD'")
                .param("id", timeout)
                .query(String.class)
                .single())
        .isEqualTo("CANCELLED");
  }

  /** 微秒字段必须用同精度比较，否则当前秒写入的消息不能立即抢占，只能等待扫描。 */
  @Test
  @Transactional
  void outboxCanBeClaimedWithinTheSameFractionalSecond() {
    var messages = new com.bitmall.agent.order.dao.repository.OrderMessageRepository(messageMapper);
    try {
      jdbc.sql("SET timestamp=1800000000.5").update();
      String id = UUID.randomUUID().toString();
      messages.insert(id, "precision-order", "precision-topic", "ORDER_CREATED", "{}");
      assertThat(
              messages.claim(id, "precision-owner", java.time.LocalDateTime.now().plusMinutes(1)))
          .isTrue();
    } finally {
      jdbc.sql("SET timestamp=0").update();
    }
  }

  @Autowired com.bitmall.agent.order.dao.mapper.OrderMessageMapper messageMapper;

  /** 新元数据和 LIKE 转义在 MySQL 方言上也必须保持精确品牌、类别过滤。 */
  @Test
  void brandPolicyFiltersAndSourcesWorkOnRealMySQL() {
    var result = knowledge.retrieve("苹果 iPhone 退货政策 % _", null, null, null);
    assertThat(result.getStatus()).isEqualTo("FOUND");
    assertThat(result.getHits())
        .hasSize(1)
        .allMatch(h -> "APPLE".equals(h.getBrand()) && "RETURNS".equals(h.getDocumentType()));
    assertThat(result.getHits().get(0).getSourceCheckedAt()).isEqualTo("2026-11-17");
    assertThat(knowledge.retrieve("Nike保修多久", null, null, null).getStatus()).isEqualTo("NO_MATCH");
    assertThat(knowledge.retrieve("苹果和耐克的退货政策", null, null, null).getHits())
        .extracting(h -> h.getBrand())
        .containsExactly("APPLE", "NIKE");
  }
}
