package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** order_message_outbox 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_message_outbox")
@Getter
@Setter
public class OrderMessageEntity {
  @TableId(value = "event_id", type = IdType.INPUT)
  private String eventId;

  @TableField("order_id")
  private String orderId;

  @TableField("topic")
  private String topic;

  @TableField("event_type")
  private String eventType;

  @TableField("payload")
  private String payload;

  /** 定时消息绝对投递时刻；即时消息不填写。 */
  @TableField("deliver_at_epoch_ms")
  private Long deliverAtEpochMillis;

  @TableField("status")
  private String status;

  @TableField("attempts")
  private Integer attempts;

  @TableField("next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @TableField("lease_token")
  private String leaseToken;

  @TableField("locked_until")
  private LocalDateTime lockedUntil;

  @TableField("broker_msg_id")
  private String brokerMsgId;

  @TableField("last_error")
  private String lastError;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("sent_at")
  private LocalDateTime sentAt;

  @TableField("trace_id")
  private String traceId;
}
