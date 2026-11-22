package com.bitmall.agent.aftersales.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** after_sales 表的可写持久化对象，不直接作为接口返回。 */
@TableName("after_sales")
@Getter
@Setter
public class AfterSalesEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("order_id")
  private String orderId;

  @TableField("user_id")
  private Long userId;

  @TableField("type")
  private String type;

  @TableField("reason")
  private String reason;

  @TableField("status")
  private String status;

  @TableField("refund_amount")
  private BigDecimal refundAmount;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
