package com.bitmall.agent.order.mq.consumer;

import com.bitmall.agent.order.service.OrderRecoveryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

/** 独立读取建单死信，核对原购买后交给同一套恢复事务。 */
@Component
@ConditionalOnProperty(
    name = {"app.order-messaging.enabled", "app.order-messaging.build-dead-letter-enabled"},
    havingValue = "true")
@RocketMQMessageListener(
    topic = "%DLQ%${app.order-messaging.build-consumer-group:wowo_mall_order_build_dev}",
    consumerGroup = "${app.order-messaging.build-consumer-group:wowo_mall_order_build_dev}_dlq",
    selectorExpression = "ORDER_BUILD",
    consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET,
    consumeThreadNumber = 1,
    consumeThreadMax = 1,
    // Broker 保留原消费次数，进入死信时已是 6。为恢复阶段预留 3 次额外重试。
    maxReconsumeTimes = OrderBuildConsumer.MAX_RETRIES + 4)
@RequiredArgsConstructor
public class OrderBuildDeadLetterConsumer implements RocketMQListener<MessageExt> {
  private static final Logger log = LoggerFactory.getLogger(OrderBuildDeadLetterConsumer.class);

  /** 解析死信消息正文。 */
  private final ObjectMapper json;

  /** 核对死信对应的购买任务，并按当前状态恢复。 */
  private final OrderRecoveryService recovery;

  /** 无法对应的消息只登记排查线索，数据库异常交给 RocketMQ 重试。 */
  @Override
  public void onMessage(MessageExt message) {
    String traceId;
    String eventId = message.getKeys();
    try {
      JsonNode document = json.readTree(message.getBody());
      traceId = document == null ? "" : document.path("traceId").asText();
      UUID.fromString(traceId);
      if (eventId == null) {
        throw new IllegalArgumentException("缺少消息 Key");
      }
      UUID.fromString(eventId);
    } catch (JacksonException | IllegalArgumentException malformed) {
      log.warn("异步建单死信格式不正确，messageId={}", message.getMsgId());
      return;
    }
    if (!recovery.reconcileBuildDeadLetter(traceId, eventId)) {
      log.warn(
          "异步建单死信无法匹配原建单记录，messageId={}，traceId={}，eventId={}",
          message.getMsgId(),
          traceId,
          eventId);
      return;
    }
    log.info("异步建单死信核对完成，traceId={}，eventId={}", traceId, eventId);
  }
}
