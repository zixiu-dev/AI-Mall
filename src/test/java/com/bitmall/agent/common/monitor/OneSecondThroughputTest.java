package com.bitmall.agent.common.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.monitor.OrderMetrics;
import com.bitmall.agent.order.monitor.OrderPressureWindow;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 校验整秒边界、提交/回滚、并发不丢数和 HTTP 拒绝分离，避免再次把区间均值冒充一秒峰值。 */
class OneSecondThroughputTest {
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(OneSecondThroughput metrics) {
    return (List<Map<String, Object>>) metrics.snapshot().get("seconds");
  }

  @Test
  void boundariesExcludeTheOpenSecondAndDoNotAverageTwoSeconds() {
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1999L);
    var metrics = new OneSecondThroughput(true, clock, 10);
    metrics.httpCompleted(true, 200);
    metrics.httpCompleted(true, 200);
    assertThat(rows(metrics)).isEmpty();
    when(clock.millis()).thenReturn(2000L);
    metrics.httpCompleted(true, 200);
    assertThat(rows(metrics)).hasSize(1);
    assertThat(rows(metrics).get(0))
        .containsEntry("epochSecond", 1L)
        .containsEntry("queryResponses", 2L);
    when(clock.millis()).thenReturn(3000L);
    assertThat(rows(metrics).get(1)).containsEntry("queryResponses", 1L);
  }

  @Test
  void tpsIsRecordedInTheCommitSecondAndRollbackDoesNotCount() {
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1000L);
    var seconds = new OneSecondThroughput(true, clock, 10);
    var properties = mock(OrderProcessingProperties.class);
    when(properties.getMaxConcurrent()).thenReturn(1);
    var metrics =
        new OrderMetrics(new SimpleMeterRegistry(), properties, new OrderPressureWindow(), seconds);
    TransactionSynchronizationManager.initSynchronization();
    try {
      metrics.committed("created", "ASYNC");
      // SQL 在第一秒执行，提交回调在第二秒：TPS 必须计入第二秒。
      when(clock.millis()).thenReturn(2000L);
      for (var synchronization : TransactionSynchronizationManager.getSynchronizations())
        synchronization.afterCommit();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.initSynchronization();
    try {
      metrics.committed("created", "ASYNC");
      // 模拟回滚：框架不执行 afterCommit。
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    when(clock.millis()).thenReturn(3000L);
    assertThat(rows(seconds))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .containsEntry("epochSecond", 2L)
                    .containsEntry("ordersCreated", 1L));
  }

  @Test
  void concurrentResponsesAndAcceptedTasksRemainSeparateFromCreatedOrders() throws Exception {
    Clock clock = Clock.fixed(java.time.Instant.ofEpochMilli(1000), java.time.ZoneOffset.UTC);
    Clock mutable = mock(Clock.class);
    when(mutable.millis()).thenReturn(clock.millis());
    var metrics = new OneSecondThroughput(true, mutable, 10);
    var executor = Executors.newFixedThreadPool(8);
    for (int thread = 0; thread < 8; thread++)
      executor.submit(
          () -> {
            for (int i = 0; i < 1000; i++) {
              metrics.httpCompleted(false, 200);
              metrics.orderTransition("accepted", "ASYNC");
            }
          });
    executor.shutdown();
    assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    metrics.httpCompleted(false, 429);
    when(mutable.millis()).thenReturn(2000L);
    assertThat(rows(metrics))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .containsEntry("submitResponses", 8001L)
                    .containsEntry("submitHttpSuccess", 8000L)
                    .containsEntry("asyncAccepted", 8000L)
                    .containsEntry("ordersCreated", 0L));
  }

  @Test
  void filterIgnoresTokenIssuanceAndCountsEscapedFailures() throws Exception {
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1000L);
    var metrics = new OneSecondThroughput(true, clock, 10);
    var filter = new ThroughputHttpFilter(metrics);
    filter.doFilter(
        new MockHttpServletRequest("POST", "/api/store/order-tokens"),
        new MockHttpServletResponse(),
        (request, response) -> {});
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/store/products/108"),
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      throw new IllegalStateException("failure");
                    }))
        .isInstanceOf(IllegalStateException.class);
    when(clock.millis()).thenReturn(2000L);
    assertThat(rows(metrics))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .containsEntry("queryResponses", 1L)
                    .containsEntry("querySuccess", 0L)
                    .containsEntry("submitResponses", 0L));
  }

  @Test
  void cacheConstructionIsVisibleInTheSameSecondAsHttpCompletions() {
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1000L);
    var seconds = new OneSecondThroughput(true, clock, 10);
    var cache =
        new com.bitmall.agent.product.cache.CacheMetrics(new SimpleMeterRegistry(), seconds);
    cache.increment("database_load");
    cache.increment("rebuild_filled");
    cache.increment("rebuild_wait");
    cache.increment("rebuild_avoided_after_local_lock");
    cache.increment("rebuild_avoided_after_redis_lock");
    cache.increment("local_hit");
    seconds.httpCompleted(true, 200);
    when(clock.millis()).thenReturn(2000L);
    assertThat(rows(seconds))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .containsEntry("querySuccess", 1L)
                    .containsEntry("cacheLoads", 1L)
                    .containsEntry("cacheFills", 1L)
                    .containsEntry("cacheRedisWaits", 1L)
                    .containsEntry("cacheDoubleCheckHits", 2L));
  }

  @Test
  void disabledMetricsAndRetentionAreBounded() {
    var disabled = new OneSecondThroughput(false);
    disabled.httpCompleted(true, 200);
    disabled.orderTransition("created", "SYNC");
    assertThat(rows(disabled)).isEmpty();
    Clock clock = mock(Clock.class);
    var metrics = new OneSecondThroughput(true, clock, 3);
    for (long second = 0; second < 10; second++) {
      when(clock.millis()).thenReturn(second * 1000);
      metrics.orderTransition("created", "SYNC");
    }
    when(clock.millis()).thenReturn(10_000L);
    assertThat(rows(metrics))
        .hasSize(2)
        .allSatisfy(row -> assertThat((Long) row.get("epochSecond")).isBetween(8L, 9L));
  }
}
