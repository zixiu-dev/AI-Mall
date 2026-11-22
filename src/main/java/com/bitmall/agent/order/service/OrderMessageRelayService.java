package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.dao.entity.OrderMessageDO;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.mq.producer.OrderEventProducer;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 后台转发本地订单消息。数据库抢占、网络发送、结果更新分开执行，不把 MQ 网络等待放进订单事务。 */
@Service
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderMessageRelayService {
  /** 仅记录事件编号和失败原因，方便定位发送积压。 */
  private static final Logger log = LoggerFactory.getLogger(OrderMessageRelayService.class);

  /** 扫描待投递的 Outbox 消息，抢占投递租约，记录投递成功或安排重试。 */
  private final OrderMessageRepository mapper;

  /** 把持久化订单消息发送到 RocketMQ，返回可核对的发送结果。 */
  private final OrderEventProducer producer;

  /** 读取每批投递数量、租约时长和重试间隔。 */
  private final OrderMessagingProperties properties;

  /** 定期扫描到期消息；多实例查到同一行时，也只有条件抢占成功的实例可以发送。 */
  @Scheduled(
      fixedDelayString = "${app.order-messaging.relay-interval-ms:5000}",
      initialDelayString = "${app.order-messaging.relay-interval-ms:5000}")
  public void relayPendingMessages() {
    for (OrderMessageDO message : mapper.findReady(properties.getBatchSize())) {
      sendOne(message);
    }
  }

  /** 提交后尝试立即发送，不等待下一轮扫描；已经抢占或发送过的事件会直接跳过。 */
  public void relayEvent(String eventId) {
    mapper.findById(eventId).ifPresent(this::sendOne);
  }

  private void sendOne(OrderMessageDO message) {
    String token = UUID.randomUUID().toString();
    if (!mapper.claim(
        message.getEventId(), token, LocalDateTime.now().plusSeconds(properties.getLeaseSeconds()))) {
      return;
    }
    try {
      String brokerMessageId = producer.send(message);
      if (!mapper.markSent(message.getEventId(), token, brokerMessageId)) {
        log.warn("消息发送成功但租约已变化，eventId={}", message.getEventId());
      }
    } catch (Exception ex) {
      long delay =
          Math.min(300, (long) properties.getRetryDelaySeconds() * (message.getAttempts() + 1L));
      mapper.scheduleRetry(
          message.getEventId(), token, LocalDateTime.now().plusSeconds(delay), ex.getMessage());
      log.warn("订单消息发送失败，稍后重试，eventId={}", message.getEventId(), ex);
    }
  }
}
