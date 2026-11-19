package com.bitmall.agent.order.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.order.config.*;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.idempotent.OrderRedisClient;
import com.bitmall.agent.order.inventory.InventorySettlementService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.mq.BrokerHealthChecker;
import com.bitmall.agent.order.service.*;
import java.net.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 使用真实 Redis 协调模式；通过可控压力样本验证切换、滞回、过载拒绝和购买路由冻结。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:auto-routing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.inventory.enabled=true",
      "app.order-processing.submission-mode=AUTO",
      "app.order-routing.initial-delay-ms=3600000",
      "app.order-routing.high-samples=2",
      "app.order-routing.low-samples=2",
      "app.order-routing.cooldown=0s"
    })
@ActiveProfiles("demo")
class OrderAutoRoutingIntegrationTest {
  private static Process server;
  private static final String namespace = "test:auto:" + UUID.randomUUID() + ":";

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    server =
        new ProcessBuilder(
                "redis-server",
                "--bind",
                "127.0.0.1",
                "--port",
                String.valueOf(port),
                "--save",
                "",
                "--appendonly",
                "no",
                "--dir",
                Files.createTempDirectory("auto-route-redis-").toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectErrorStream(true)
            .start();
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (true) {
      try (var socket = new Socket("127.0.0.1", port)) {
        break;
      } catch (Exception ex) {
        if (System.nanoTime() > until) throw ex;
        Thread.sleep(20);
      }
    }
    registry.add("app.order-processing.redis-port", () -> port);
    registry.add("app.order-processing.namespace", () -> namespace);
  }

  @AfterAll
  static void stop() {
    if (server != null) server.destroy();
  }

  @Autowired OrderPressureRouter router;
  @Autowired tools.jackson.databind.ObjectMapper json;
  @Autowired OrderRedisClient redis;
  @Autowired OrderSubmissionService submit;
  @Autowired OrderTraceService traces;
  @Autowired CheckoutService checkout;
  @Autowired OrderBuildCommandService consumer;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired InventorySettlementService settlement;
  @MockitoSpyBean OrderPressureWindow window;
  @MockitoSpyBean OrderMessagingProperties messaging;
  @MockitoBean BrokerHealthChecker broker;
  @MockitoSpyBean OrderAutoRoutingProperties limits;
  @MockitoSpyBean OrderTraceRepository traceRows;

  private String state() {
    return namespace + "{routing}:state";
  }

  @BeforeEach
  void setup() {
    redis
        .template()
        .delete(List.of(state(), namespace + "{routing}:nodes", namespace + "{routing}:samples"));
    doReturn(true).when(messaging).isEnabled();
    when(broker.isHealthy()).thenReturn(true);
    low();
    tick();
  }

  private void low() {
    doReturn(Map.of("qps", 0.0, "syncSamples", 0L, "syncP95Ms", 0)).when(window).snapshot();
  }

  private void high() {
    doReturn(Map.of("qps", 300.0, "syncSamples", 40L, "syncP95Ms", 400)).when(window).snapshot();
  }

  private void tick() {
    redis.template().opsForHash().put(state(), "evaluatedAt", "0");
    router.sample();
  }

  @Test
  void switchesOnlyAfterConsecutiveHighAndRecoversAfterConsecutiveLow() {
    assertThat(router.select()).isEqualTo("SYNC");
    high();
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
    tick();
    assertThat(router.select()).isEqualTo("ASYNC");
    low();
    tick();
    assertThat(router.select()).isEqualTo("ASYNC");
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
  }

  @Test
  void multipleReportsWithinOneSecondDoNotArtificiallyIncreaseConsecutiveSamples() {
    high();
    tick();
    router.sample();
    router.sample();
    assertThat(router.status().get("highSamples")).isEqualTo(1);
    assertThat(router.select()).isEqualTo("SYNC");
  }

  @Test
  void cooldownAndNeutralBandPreventFlapping() {
    doReturn(Duration.ofSeconds(30)).when(limits).getCooldown();
    high();
    tick();
    tick();
    assertThat(router.select()).isEqualTo("ASYNC");
    low();
    tick();
    tick();
    assertThat(router.select()).isEqualTo("ASYNC");
    redis
        .template()
        .opsForHash()
        .put(state(), "changedAt", String.valueOf(System.currentTimeMillis() - 31_000));
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
  }

  @Test
  void queueOverloadRejectsNewAdmissionsWithoutFallingBackToDirectDatabaseOrders() {
    doReturn(Map.of("pending", 1001L)).when(traceRows).backlog();
    tick();
    assertThat(router.status().get("reason")).isEqualTo("QUEUE_OVERLOADED");
    assertThatThrownBy(router::select).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void oldQueueAlsoBlocksWhenCountIsSmall() {
    doReturn(Map.of("pending", 1L, "oldest", java.time.LocalDateTime.now().minusMinutes(1)))
        .when(traceRows)
        .backlog();
    tick();
    assertThatThrownBy(router::select).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void failedOrStaleSamplingCannotBeMistakenForLowLoad() {
    doThrow(new IllegalStateException("temporary database outage")).when(traceRows).backlog();
    router.sample();
    assertThatThrownBy(router::select).isInstanceOf(ResponseStatusException.class);
    doCallRealMethod().when(traceRows).backlog();
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
    ReflectionTestUtils.setField(
        router, "sampledAt", System.nanoTime() - Duration.ofSeconds(10).toNanos());
    assertThatThrownBy(router::select).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void unavailableMqRejectsHighPressureInsteadOfReturningToSync() {
    when(broker.isHealthy()).thenReturn(false);
    high();
    tick();
    tick();
    assertThat(router.status().get("mode")).isEqualTo("ASYNC");
    assertThat(router.status().get("reason")).isEqualTo("ASYNC_UNAVAILABLE");
    assertThatThrownBy(router::select).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void firstPreDeductionUsesCurrentModeAndSubsequentRetriesKeepIt() {
    var quote = checkout.checkoutQuote(10001, 1003, 1, 20001L, null);
    var command = new CreateOrderCommand("DIRECT", 1003L, 1, 20001L, null, quote.getPayAmount());
    var prepared = traces.prepare(10001, command, null);
    assertThat(prepared.getMode()).isEqualTo("SYNC");
    high();
    tick();
    tick();
    assertThat(submit.submit(10001, prepared.getTraceId()).getMode()).isEqualTo("ASYNC");
    assertThat(traces.get(10001, prepared.getTraceId()).getStatus()).isEqualTo("PROCESSING");
    low();
    tick();
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
    assertThat(submit.submit(10001, prepared.getTraceId()).getMode()).isEqualTo("ASYNC");
    consumer.consume(prepared.getTraceId());
    settlement.settleTrace(prepared.getTraceId());
    var order = traces.get(10001, prepared.getTraceId());
    assertThat(order.getStatus()).isEqualTo("SUCCESS");
    lifecycle.cancel(10001, order.getOrderId());
    settlement.settleReturn(order.getOrderId(), 1003);
  }

  @Test
  void aggregatesOtherInstancesAndRemovesExpiredSamples() throws Exception {
    var sample = new LinkedHashMap<String, Object>();
    sample.put("qps", 300);
    sample.put("capacity", 60);
    sample.put("inflight", 0);
    sample.put("permits", 32);
    sample.put("syncSamples", 40);
    sample.put("syncP95Ms", 400);
    sample.put("databaseUsage", 0);
    sample.put("databaseWaiters", 0);
    sample.put("pending", 0);
    sample.put("oldestSeconds", 0);
    sample.put("healthy", true);
    sample.put("asyncReady", true);
    redis
        .template()
        .opsForHash()
        .put(namespace + "{routing}:samples", "other", json.writeValueAsString(sample));
    redis
        .template()
        .opsForZSet()
        .add(namespace + "{routing}:nodes", "other", System.currentTimeMillis());
    tick();
    tick();
    assertThat(router.select()).isEqualTo("ASYNC");
    assertThat(router.status().get("nodes")).isEqualTo(2);
    redis.template().opsForZSet().add(namespace + "{routing}:nodes", "other", 0);
    tick();
    tick();
    assertThat(router.select()).isEqualTo("SYNC");
    assertThat(router.status().get("nodes")).isEqualTo(1);
    assertThat(redis.template().opsForHash().hasKey(namespace + "{routing}:samples", "other"))
        .isFalse();
  }

  @Test
  void freshSyncSubmissionBuildsImmediately() {
    var q = checkout.checkoutQuote(10001, 1003, 1, 20001L, null);
    var p =
        traces.prepare(
            10001,
            new CreateOrderCommand("DIRECT", 1003L, 1, 20001L, null, q.getPayAmount()),
            null);
    var result = submit.submit(10001, p.getTraceId());
    assertThat(result.getMode()).isEqualTo("SYNC");
    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    settlement.settleTrace(p.getTraceId());
    lifecycle.cancel(10001, result.getOrderId());
    settlement.settleReturn(result.getOrderId(), 1003);
  }
}
