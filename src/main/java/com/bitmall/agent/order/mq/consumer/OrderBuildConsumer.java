package com.bitmall.agent.order.mq.consumer;

import com.bitmall.agent.order.service.OrderBuildCommandService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
    maxReconsumeTimes = OrderBuildConsumer.MAX_RETRIES)
@RequiredArgsConstructor
public class OrderBuildConsumer implements RocketMQListener<String> {
  static final int MAX_RETRIES = 5;

  /** 解析建单消息正文。 */
  private final ObjectMapper json;

  /** 接收建单消息，调用建单事务并在回滚后记录业务失败。 */
  private final OrderBuildCommandService commands;

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
