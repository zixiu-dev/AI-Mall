package com.bitmall.agent.coupon.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** coupon 表的可写持久化对象，不直接作为接口返回。 */
@TableName("coupon")
@Getter
@Setter
public class CouponEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("name")
  private String name;

  @TableField("threshold_amount")
  private BigDecimal thresholdAmount;

  @TableField("discount_amount")
  private BigDecimal discountAmount;

  @TableField("total_stock")
  private Integer totalStock;

  @TableField("claimed_count")
  private Integer claimedCount;

  @TableField("start_at")
  private LocalDateTime startAt;

  @TableField("end_at")
  private LocalDateTime endAt;

  @TableField("status")
  private String status;
}
