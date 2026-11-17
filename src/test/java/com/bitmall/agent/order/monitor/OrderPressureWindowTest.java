package com.bitmall.agent.order.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 验证十秒窗口过期和压力拒绝状态；拒绝请求也必须计入真实尝试量。 */
class OrderPressureWindowTest {
  @Test
  void boundedWindowExpiresOldPressure() {
    var clock = mock(Clock.class);
    when(clock.millis()).thenReturn(10_000L);
    var window = new OrderPressureWindow(clock);
    for (int i = 0; i < 100; i++) {
      window.request();
      window.syncCompleted(250_000_000L);
    }
    assertThat(window.snapshot().get("syncP95Ms")).isEqualTo(260);
    when(clock.millis()).thenReturn(21_000L);
    assertThat(window.snapshot().get("qps")).isEqualTo(0.0);
    assertThat(window.snapshot().get("syncSamples")).isEqualTo(0L);
  }

  @Test
  void concurrentLimitReturns429AndCountsRejectedAttempt() {
    var properties = mock(OrderProcessingProperties.class);
    when(properties.getMaxConcurrent()).thenReturn(1);
    var window = new OrderPressureWindow();
    var metrics = new OrderMetrics(new SimpleMeterRegistry(), properties, window);
    long entered = metrics.enter();
    try {
      assertThatThrownBy(metrics::enter)
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
      assertThat((Double) window.snapshot().get("qps")).isEqualTo(2.0);
    } finally {
      metrics.exit(entered, "SYNC");
    }
    assertThat(metrics.inFlight()).isZero();
  }

  @Test
  void tpsCountsCompletedOrdersAndExpiresWithTheWindow() {
    var clock = mock(Clock.class);
    when(clock.millis()).thenReturn(20_000L);
    var window = new OrderPressureWindow(clock);
    for (int i = 0; i < 20; i++) window.request();
    for (int i = 0; i < 3; i++) window.orderCreated();
    assertThat(window.snapshot().get("qps")).isEqualTo(20.0);
    assertThat(window.snapshot().get("tps")).isEqualTo(3.0);
    when(clock.millis()).thenReturn(31_000L);
    assertThat(window.snapshot().get("tps")).isEqualTo(0.0);
  }

  @Test
  void acceptingOrFailingAsyncTaskIsNotCountedAsSuccessfulTransaction() {
    var properties = mock(OrderProcessingProperties.class);
    when(properties.getMaxConcurrent()).thenReturn(1);
    var metrics =
        new OrderMetrics(new SimpleMeterRegistry(), properties, new OrderPressureWindow());
    metrics.committed("accepted", "ASYNC");
    metrics.committed("failed", "ASYNC");
    assertThat(metrics.snapshot().get("ordersCreatedTotal")).isEqualTo(0.0);
    assertThat(metrics.snapshot().get("tps")).isEqualTo(0.0);
    metrics.committed("created", "ASYNC");
    assertThat(metrics.snapshot().get("ordersCreatedTotal")).isEqualTo(1.0);
    assertThat(metrics.snapshot().get("tps")).isEqualTo(1.0);
  }
}
