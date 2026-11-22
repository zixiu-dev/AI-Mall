package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** order_event_receipt 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_event_receipt")
@Getter
@Setter
public class OrderEventReceiptEntity {
  @TableId(value = "event_id", type = IdType.INPUT)
  private String eventId;

  @TableField("order_id")
  private String orderId;

  @TableField("event_type")
  private String eventType;

  @TableField("user_id")
  private Long userId;

  @TableField("pay_amount")
  private BigDecimal payAmount;

  @TableField("occurred_at")
  private LocalDateTime occurredAt;

  @TableField("received_at")
  private LocalDateTime receivedAt;
}
