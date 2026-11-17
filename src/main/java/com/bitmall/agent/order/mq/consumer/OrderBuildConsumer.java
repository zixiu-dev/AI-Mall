package com.bitmall.agent.order.mq.consumer;

import com.bitmall.agent.order.service.OrderBuildCommandService;
import java.util.UUID;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 新排队链路只订阅 ORDER_BUILD，与已退役的活动 ORDER_SUBMIT 消息隔离。消息只传 trace。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.order-messaging.topic}",
    consumerGroup = "${app.order-messaging.build-consumer-group:wowo_mall_order_build_dev}",
    selectorExpression = "ORDER_BUILD",
    consumeThreadNumber = 2,
    consumeThreadMax = 4,
    maxReconsumeTimes = 5)
public class OrderBuildConsumer implements RocketMQListener<String> {
  private final ObjectMapper json;
  private final OrderBuildCommandService commands;

  public OrderBuildConsumer(ObjectMapper json, OrderBuildCommandService commands) {
    this.json = json;
    this.commands = commands;
  }

  @Override
  public void onMessage(String payload) {
    String id;
    try {
      id = json.readTree(payload).path("traceId").asText();
      UUID.fromString(id);
    } catch (Exception invalid) {
      throw new IllegalArgumentException("异步建单消息缺少有效购买编号", invalid);
    }
    commands.consume(id);
  }
}
