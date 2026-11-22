package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.bitmall.agent.order.mq.message.OutboxReadyEvent;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/** 将订单变更保存成本地消息。必须加入已有订单事务，不能先提交订单再补写消息；此服务不发起网络调用。 */
@Service
@RequiredArgsConstructor
public class OrderMessageService {
  /** 写入 Outbox 消息（普通事件和建单命令）。 */
  private final OrderMessageRepository mapper;

  /** 把消息正文序列化成 JSON 存入 Outbox。 */
  private final ObjectMapper json;

  /** 读取订单消息 Topic。 */
  private final OrderMessagingProperties properties;

  /** 写入消息后发布 OutboxReadyEvent，通知投递任务尽快发送这条消息。 */
  private final ApplicationEventPublisher events;

  /** 真正触发异步建单的命令；不把订单事件误当建单请求。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueBuild(String traceId) {
    String eventId =
        UUID.nameUUIDFromBytes(
                ("ORDER_BUILD:" + traceId).getBytes(StandardCharsets.UTF_8))
            .toString();
    try {
      mapper.insertBuild(
          eventId,
          traceId,
          properties.getTopic(),
          json.writeValueAsString(Map.of("traceId", traceId)));
      events.publishEvent(new OutboxReadyEvent(eventId));
    } catch (JacksonException ex) {
      throw new IllegalStateException("建单命令序列化失败，本次受理回滚", ex);
    }
  }

  /** 建单事务里记录一次超时检查，使用原始截止时刻；重发不能把支付期限向后推。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueuePaymentTimeout(String orderId, long userId, LocalDateTime deadline) {
    String eventId =
        UUID.nameUUIDFromBytes(
                (OrderPaymentTimeoutMessage.TYPE + ":" + orderId)
                    .getBytes(StandardCharsets.UTF_8))
            .toString();
    long deliverAt = deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    var command = new OrderPaymentTimeoutMessage(eventId, orderId, userId, deliverAt);
    try {
      mapper.insert(
          eventId,
          orderId,
          properties.getTopic(),
          OrderPaymentTimeoutMessage.TYPE,
          json.writeValueAsString(command),
          deliverAt);
      events.publishEvent(new OutboxReadyEvent(eventId));
    } catch (JacksonException ex) {
      throw new IllegalStateException("支付超时命令序列化失败，建单事务将回滚", ex);
    }
  }

  /** 为一次已完成的订单变更创建稳定事件编号，在同一数据库事务中记录发送原文。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(String eventType, String orderId, long userId, BigDecimal payAmount) {
    OrderEventMessage message =
        new OrderEventMessage(
            UUID.randomUUID().toString(),
            eventType,
            orderId,
            userId,
            payAmount,
            LocalDateTime.now());
    try {
      mapper.insert(
          message.getEventId(),
          orderId,
          properties.getTopic(),
          eventType,
          json.writeValueAsString(message));
      events.publishEvent(
          new OutboxReadyEvent(message.getEventId()));
    } catch (JacksonException ex) {
      throw new IllegalStateException("订单事件序列化失败，订单事务将回滚", ex);
    }
  }
}
