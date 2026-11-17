package com.bitmall.agent.order.mq.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 在 MQ 中传输的订单事件。只包含事件编号、订单编号、用户、金额和发生时间，不传收货地址、手机号或模型上下文。 */
public final class OrderEventMessage {
  /** 待支付订单已经创建，不代表支付完成。 */
  public static final String CREATED = "ORDER_CREATED";

  /** 已经确认支付的订单变更，与建单事件区分。 */
  public static final String PAID = "ORDER_PAID";

  /** 订单已经成功取消。 */
  public static final String CANCELLED = "ORDER_CANCELLED";

  /** 一次业务事件的稳定编号；重试发送时沿用，供消费者去重。 */
  private final String eventId;

  /** 事件类型，同时作为 MQ Tag。 */
  private final String eventType;

  /** 发生变更的订单编号。 */
  private final String orderId;

  /** 订单所属用户编号。 */
  private final long userId;

  /** 订单成交金额；取消事件也保留原订单金额。 */
  private final BigDecimal payAmount;

  /** 业务事件生成时间，与发送、重试时间区分。 */
  private final LocalDateTime occurredAt;

  /** 显式绑定 JSON 字段并检查消息内容，格式错误时让消费者重试，而不是当作成功处理。 */
  @JsonCreator
  public OrderEventMessage(
      @JsonProperty("eventId") String eventId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("userId") long userId,
      @JsonProperty("payAmount") BigDecimal payAmount,
      @JsonProperty("occurredAt") LocalDateTime occurredAt) {
    if (eventId == null
        || eventId.isBlank()
        || eventId.length() > 40
        || orderId == null
        || orderId.isBlank()
        || orderId.length() > 40
        || (!CREATED.equals(eventType) && !CANCELLED.equals(eventType) && !PAID.equals(eventType))
        || userId <= 0
        || payAmount == null
        || payAmount.signum() < 0
        || occurredAt == null) {
      throw new IllegalArgumentException("订单事件缺少有效字段或事件类型不支持");
    }
    this.eventId = eventId;
    this.eventType = eventType;
    this.orderId = orderId;
    this.userId = userId;
    this.payAmount = payAmount;
    this.occurredAt = occurredAt;
  }

  /** 获取幂等事件编号。 */
  public String getEventId() {
    return eventId;
  }

  /** 获取事件类型。 */
  public String getEventType() {
    return eventType;
  }

  /** 获取订单编号。 */
  public String getOrderId() {
    return orderId;
  }

  /** 获取所属用户编号。 */
  public long getUserId() {
    return userId;
  }

  /** 获取订单金额。 */
  public BigDecimal getPayAmount() {
    return payAmount;
  }

  /** 获取事件生成时间。 */
  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }
}
