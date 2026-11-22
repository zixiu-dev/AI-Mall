package com.bitmall.agent.order.mq.producer;

import com.bitmall.agent.order.dao.entity.OrderMessageDO;
import com.bitmall.agent.order.mq.message.OrderPaymentTimeoutMessage;

import lombok.RequiredArgsConstructor;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** RocketMQ 发送适配器。以事件编号作为消息 Key，只有 Broker 返回 SEND_OK 才算成功。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderEventProducer {
    /** 同步发送订单消息；超时关单消息按指定时间延迟投递。 */
    private final RocketMQTemplate template;

    /** 把数据库保存的原文发往 Topic:Tag；异常或非成功状态由后台转为重试。 */
    public String send(OrderMessageDO message) {
        String destination = message.getTopic() + ":" + message.getEventType();
        var payload =
                MessageBuilder.withPayload(message.getPayload())
                        .setHeader(RocketMQHeaders.KEYS, message.getEventId())
                        .build();
        Long deliverAt = message.getDeliverAtEpochMillis();
        if (OrderPaymentTimeoutMessage.TYPE.equals(message.getEventType())
                && (deliverAt == null || deliverAt <= 0)) {
            throw new IllegalArgumentException("支付超时命令缺少原始投递时刻，不能立即发送");
        }
        // RocketMQ 5 使用绝对时刻投递。已过期的积压消息立即补发，不再多等 15 分钟。
        SendResult result =
                deliverAt != null && deliverAt > System.currentTimeMillis()
                        ? template.syncSendDeliverTimeMills(destination, payload, deliverAt)
                        : template.syncSend(destination, payload);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("Broker 未确认订单事件发送成功");
        }
        return result.getMsgId();
    }
}
