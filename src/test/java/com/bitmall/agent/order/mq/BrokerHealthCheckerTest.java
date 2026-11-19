package com.bitmall.agent.order.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 验证订单 Topic 探针的冷启动、短暂失败、恢复和过期。 */
class BrokerHealthCheckerTest {
  private final RocketMQTemplate rocketMQ = mock(RocketMQTemplate.class);
  private final OrderMessagingProperties messaging = mock(OrderMessagingProperties.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final BrokerHealthChecker checker = new BrokerHealthChecker(rocketMQ, messaging, registry);

  @Test
  void staysUnavailableUntilFirstSuccessThenToleratesTwoFailuresAndRecovers() {
    when(messaging.getTopic()).thenReturn("orders");
    var success = new SendResult();
    success.setSendStatus(SendStatus.SEND_OK);
    when(rocketMQ.syncSend(eq("orders:HEALTH_CHECK"), anyString(), anyLong()))
        .thenReturn(success)
        .thenThrow(new IllegalStateException("Broker 暂不可达"))
        .thenThrow(new IllegalStateException("Broker 暂不可达"))
        .thenThrow(new IllegalStateException("Broker 暂不可达"))
        .thenReturn(success);

    assertThat(checker.isHealthy()).isFalse();
    checker.check();
    assertThat(checker.isHealthy()).isTrue();

    checker.check();
    checker.check();
    assertThat(checker.isHealthy()).isTrue();
    checker.check();
    assertThat(checker.isHealthy()).isFalse();
    assertThat(checker.getConsecutiveFailures()).isEqualTo(3);

    checker.check();
    assertThat(checker.isHealthy()).isTrue();
    assertThat(checker.getConsecutiveFailures()).isZero();
    assertThat(registry.counter("mall.order.broker.check_failed").count()).isEqualTo(3);
    assertThat(registry.counter("mall.order.broker.unhealthy").count()).isEqualTo(1);
    assertThat(registry.counter("mall.order.broker.recovered").count()).isEqualTo(1);
  }

  @Test
  void staleProbeDisablesAdmissionAndMetric() {
    when(messaging.getTopic()).thenReturn("orders");
    var success = new SendResult();
    success.setSendStatus(SendStatus.SEND_OK);
    when(rocketMQ.syncSend(eq("orders:HEALTH_CHECK"), anyString(), anyLong())).thenReturn(success);

    checker.check();
    assertThat(registry.get("mall.order.broker.healthy").gauge().value()).isEqualTo(1);
    var lastSuccess = (AtomicLong) ReflectionTestUtils.getField(checker, "lastSuccessTime");
    lastSuccess.addAndGet(-31_000);
    assertThat(checker.isHealthy()).isFalse();
    assertThat(registry.get("mall.order.broker.healthy").gauge().value()).isZero();
  }
}
