package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** customer_order 表的可写持久化对象，不直接作为接口返回。 */
@TableName("customer_order")
@Getter
@Setter
public class OrderEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("user_id")
  private Long userId;

  @TableField("status")
  private String status;

  @TableField("goods_amount")
  private BigDecimal goodsAmount;

  @TableField("discount_amount")
  private BigDecimal discountAmount;

  @TableField("freight_amount")
  private BigDecimal freightAmount;

  @TableField("pay_amount")
  private BigDecimal payAmount;

  @TableField("address_snapshot")
  private String addressSnapshot;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("paid_at")
  private LocalDateTime paidAt;

  @TableField("cancelled_at")
  private LocalDateTime cancelledAt;

  @TableField("version")
  private Integer version;

  @TableField("submit_trace_id")
  private String submitTraceId;

  @TableField("coupon_id")
  private Long couponId;

  @TableField("coupon_name")
  private String couponName;
}
