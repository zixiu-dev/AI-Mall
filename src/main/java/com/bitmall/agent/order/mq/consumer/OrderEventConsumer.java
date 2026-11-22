package com.bitmall.agent.order.mq.consumer;

import com.bitmall.agent.order.mq.message.OrderEventMessage;
import com.bitmall.agent.order.service.OrderEventReceiptService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 订单事件消费入口。解析 JSON 后交给 Service 落库，重复事件直接确认，其他异常抛给 Broker 触发重试。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.order-messaging.topic}",
    consumerGroup = "${app.order-messaging.consumer-group}",
    selectorExpression = "ORDER_CREATED || ORDER_PAID || ORDER_CANCELLED",
    consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET,
    consumeThreadNumber = 2,
    consumeThreadMax = 4,
    maxReconsumeTimes = 5)
@RequiredArgsConstructor
public class OrderEventConsumer implements RocketMQListener<String> {
  /** 记录重复事件，便于排查至少一次投递行为。 */
  private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

  /** 解析订单事件消息正文。 */
  private final ObjectMapper json;

  /** 保存订单事件处理回执，并按数据库唯一约束处理重复消息。 */
  private final OrderEventReceiptService receipts;

  /** Service 事务成功才正常返回；重复主键异常必须在事务已经回滚后处理，避免 PostgreSQL 事务中止状态影响后续 SQL。 */
  @Override
  public void onMessage(String payload) {
    OrderEventMessage message;
    try {
      message = json.readValue(payload, OrderEventMessage.class);
      if (message == null) {
        throw new IllegalArgumentException("订单事件不能为空");
      }
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("订单事件格式不正确", ex);
    }
    try {
      receipts.record(message);
    } catch (DuplicateKeyException duplicate) {
      log.debug("重复订单事件已忽略，eventId={}", message.getEventId());
    }
  }
}
