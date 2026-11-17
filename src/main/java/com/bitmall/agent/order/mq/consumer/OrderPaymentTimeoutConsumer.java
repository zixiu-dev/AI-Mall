package com.bitmall.agent.order.mq.consumer;

import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;
import com.bitmall.agent.order.service.OrderLifecycleService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 独立消费延时关单命令。只负责解析与调用；幂等、期限复核和支付竞争由订单行锁/CAS 保证。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RocketMQMessageListener(
    topic = "${app.order-messaging.topic}",
    consumerGroup = "${app.order-messaging.timeout-consumer-group:wowo_mall_order_timeout_dev}",
    selectorExpression = "ORDER_PAYMENT_TIMEOUT",
    consumeThreadNumber = 2,
    consumeThreadMax = 4,
    maxReconsumeTimes = 5)
public class OrderPaymentTimeoutConsumer implements RocketMQListener<String> {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OrderPaymentTimeoutConsumer.class);
  private final ObjectMapper json;
  private final OrderLifecycleService lifecycle;

  public OrderPaymentTimeoutConsumer(ObjectMapper json, OrderLifecycleService lifecycle) {
    this.json = json;
    this.lifecycle = lifecycle;
  }

  /** 不能只信消息里的时间，更不能把解析失败、数据库异常或提前投递吞掉并 ACK。 */
  @Override
  public void onMessage(String payload) {
    OrderPaymentTimeoutMessage command;
    try {
      command = json.readValue(payload, OrderPaymentTimeoutMessage.class);
      if (command == null) throw new IllegalArgumentException("支付超时命令不能为空");
    } catch (Exception invalid) {
      throw new IllegalArgumentException("支付超时命令格式错误", invalid);
    }
    if (!lifecycle.closeExpired(command.getUserId(), command.getOrderId()))
      throw new IllegalStateException("订单尚未到支付截止时间，稍后重试超时检查");
    log.info("支付超时消息检查完成，orderId={},eventId={}", command.getOrderId(), command.getEventId());
  }
}
