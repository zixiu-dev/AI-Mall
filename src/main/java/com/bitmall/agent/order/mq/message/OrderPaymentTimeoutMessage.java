package com.bitmall.agent.order.mq.message;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 待支付超时检查命令。编号和截止时间随建单持久化；消费者仍以数据库中的订单状态和期限为准。 */
@Getter
public final class OrderPaymentTimeoutMessage {
  /** 与生命周期审计事件分开的命令 Tag。 */
  public static final String TYPE = "ORDER_PAYMENT_TIMEOUT";

  /** 一次订单超时检查的稳定编号，Outbox 重发时沿用。 */
  private final String eventId;
  /** 已真实创建的订单，异步受理时尚不生成此命令。 */
  private final String orderId;
  /** 订单归属，消费时仍通过数据库核对。 */
  private final long userId;
  /** 原支付截止的绝对时间，供定时投递；关单以数据库期限为准。 */
  private final long paymentExpiresAtEpochMillis;

  /** 校验基础格式，避免缺字段的消息被当作已经成功关单。 */
  @JsonCreator
  public OrderPaymentTimeoutMessage(
      @JsonProperty("eventId") String eventId,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("userId") long userId,
      @JsonProperty("paymentExpiresAtEpochMillis") long paymentExpiresAtEpochMillis) {
    if (eventId == null || eventId.isBlank() || eventId.length() > 40
        || orderId == null || orderId.isBlank() || orderId.length() > 40
        || userId <= 0 || paymentExpiresAtEpochMillis <= 0) {
      throw new IllegalArgumentException("支付超时命令缺少有效字段");
    }
    this.eventId = eventId;
    this.orderId = orderId;
    this.userId = userId;
    this.paymentExpiresAtEpochMillis = paymentExpiresAtEpochMillis;
  }
}
