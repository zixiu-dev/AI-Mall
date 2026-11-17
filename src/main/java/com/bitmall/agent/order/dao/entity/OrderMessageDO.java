package com.bitmall.agent.order.dao.entity;

/** 从本地待发送消息表读取的数据。这里只保留发送和重试需要的字段，SQL 与状态更新由 Mapper 负责。 */
public final class OrderMessageDO {
    /** 业务事件编号，重试不能重新生成。 */
    private final String eventId;

    /** 创建消息时确定的主题，配置变更不会偷偷更改积压消息的目标。 */
    private final String topic;

    /** 事件类型，发送时作为 Tag。 */
    private final String eventType;

    /** 与订单一起保存的 JSON 原文。 */
    private final String payload;

    /** 之前抢占发送的次数，用于计算失败退避。 */
    private final int attempts;

    /** RocketMQ 5 定时消息的绝对投递时间；普通事件为 null，发送重试不得改变它。 */
    private final Long deliverAtEpochMillis;

    /** 兼容普通即时事件。 */
    public OrderMessageDO(
            String eventId, String topic, String eventType, String payload, int attempts) {
        this(eventId, topic, eventType, payload, attempts, null);
    }

    /** 保存发送字段及原始支付截止时间，不在发送时重新计算有效期。 */
    public OrderMessageDO(
            String eventId, String topic, String eventType, String payload, int attempts,
            Long deliverAtEpochMillis) {
        this.deliverAtEpochMillis = deliverAtEpochMillis;
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.attempts = attempts;
    }

    /** 获取定时投递的 epoch 毫秒；null 表示普通事件。 */
    public Long getDeliverAtEpochMillis() {
        return deliverAtEpochMillis;
    }

    /** 获取事件编号。 */
    public String getEventId() {
        return eventId;
    }

    /** 获取目标主题。 */
    public String getTopic() {
        return topic;
    }

    /** 获取事件标签。 */
    public String getEventType() {
        return eventType;
    }

    /** 获取发送原文。 */
    public String getPayload() {
        return payload;
    }

    /** 获取已尝试次数。 */
    public int getAttempts() {
        return attempts;
    }
}
