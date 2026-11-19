package com.bitmall.agent.order.mq;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期探测实际订单 Topic 的发送能力，供异步受理读取本机快照。
 * 消费积压由订单监控单独采样。
 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
public class BrokerHealthChecker {
  private static final Logger log = LoggerFactory.getLogger(BrokerHealthChecker.class);

  private final RocketMQTemplate rocketMQTemplate;
  private final OrderMessagingProperties messagingProperties;
  private final MeterRegistry registry;
  private final AtomicBoolean healthy = new AtomicBoolean(false);
  private final AtomicLong lastSuccessTime = new AtomicLong();
  private final AtomicLong consecutiveFailures = new AtomicLong(0);

  public BrokerHealthChecker(
      RocketMQTemplate rocketMQTemplate,
      OrderMessagingProperties messagingProperties,
      MeterRegistry registry) {
    this.rocketMQTemplate = rocketMQTemplate;
    this.messagingProperties = messagingProperties;
    this.registry = registry;
    registry.gauge("mall.order.broker.healthy", this, checker -> checker.isHealthy() ? 1.0 : 0.0);
  }

  /** 每 10 秒向实际订单 Topic 发送检查消息，各业务消费组通过标签筛选。 */
  @Scheduled(fixedDelay = 10000, initialDelay = 5000)
  public void check() {
    try {
      String topic = messagingProperties.getTopic();
      String payload = "health_check:" + System.currentTimeMillis();
      SendResult result = rocketMQTemplate.syncSend(topic + ":HEALTH_CHECK", payload, 1000);

      if (result != null && result.getSendStatus() == SendStatus.SEND_OK) {
        boolean recovering = lastSuccessTime.getAndSet(System.currentTimeMillis()) != 0;
        consecutiveFailures.set(0);
        if (!healthy.getAndSet(true) && recovering) {
          log.info("Broker 健康检查恢复正常，Topic={}", topic);
          registry.counter("mall.order.broker.recovered").increment();
        }
      } else {
        handleFailure(result == null ? "缺少发送回执" : "发送状态异常: " + result.getSendStatus());
      }

    } catch (Exception e) {
      handleFailure("发送失败: " + e.getClass().getSimpleName());
    }
  }

  private void handleFailure(String reason) {
    long failures = consecutiveFailures.incrementAndGet();
    registry.counter("mall.order.broker.check_failed").increment();

    // 连续失败 3 次才标记为不健康（避免偶发抖动）
    if (failures >= 3 && healthy.getAndSet(false)) {
      log.error(
          "Broker 健康检查连续失败 {} 次，标记为不健康: {}，Topic={}",
          failures,
          reason,
          messagingProperties.getTopic());
      registry.counter("mall.order.broker.unhealthy").increment();
    }
  }

  /** 首次发送成功前或最近一次成功已过期时，异步受理暂停。 */
  public boolean isHealthy() {
    long lastSuccess = lastSuccessTime.get();
    return healthy.get() && lastSuccess != 0 && System.currentTimeMillis() - lastSuccess < 30000;
  }

  /** 获取最后成功时间（用于监控） */
  public long getLastSuccessTime() {
    return lastSuccessTime.get();
  }

  /** 获取连续失败次数（用于监控） */
  public long getConsecutiveFailures() {
    return consecutiveFailures.get();
  }
}
