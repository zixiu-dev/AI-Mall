package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 将订单变更保存成本地消息。必须加入已有订单事务，不能先提交订单再补写消息；此服务不发起网络调用。 */
@Service
public class OrderMessageService {
  /** 本地消息持久化组件。 */
  private final OrderMessageRepository mapper;

  /** 消息 JSON 转换器，与应用日期时间配置保持一致。 */
  private final ObjectMapper json;

  /** 消息主题等订单模块配置。 */
  private final OrderMessagingProperties properties;

  /** 提交后发送的本机唤醒通知，失败仍由数据库扫描补漏。 */
  private final org.springframework.context.ApplicationEventPublisher events;

  /** 注入本地消息存储、JSON 转换和主题配置。 */
  public OrderMessageService(
      OrderMessageRepository mapper,
      ObjectMapper json,
      OrderMessagingProperties properties,
      org.springframework.context.ApplicationEventPublisher events) {
    this.mapper = mapper;
    this.json = json;
    this.properties = properties;
    this.events = events;
  }

  /** 真正触发异步建单的命令；不把订单事件误当建单请求。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueBuild(String traceId) {
    String eventId =
        UUID.nameUUIDFromBytes(
                ("ORDER_BUILD:" + traceId).getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    try {
      mapper.insertBuild(
          eventId,
          traceId,
          properties.getTopic(),
          json.writeValueAsString(java.util.Map.of("traceId", traceId)));
      events.publishEvent(new com.bitmall.agent.order.mq.message.OutboxReadyEvent(eventId));
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
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    long deliverAt = deadline.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    var command = new OrderPaymentTimeoutMessage(eventId, orderId, userId, deliverAt);
    try {
      mapper.insert(
          eventId,
          orderId,
          properties.getTopic(),
          OrderPaymentTimeoutMessage.TYPE,
          json.writeValueAsString(command),
          deliverAt);
      events.publishEvent(new com.bitmall.agent.order.mq.message.OutboxReadyEvent(eventId));
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
          new com.bitmall.agent.order.mq.message.OutboxReadyEvent(message.getEventId()));
    } catch (JacksonException ex) {
      throw new IllegalStateException("订单事件序列化失败，订单事务将回滚", ex);
    }
  }
}
