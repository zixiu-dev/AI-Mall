package com.bitmall.agent.order.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** order_submit_trace 表的可写持久化对象，不直接作为接口返回。 */
@TableName("order_submit_trace")
@Getter
@Setter
public class OrderTraceEntity {
  @TableId(value = "trace_id", type = IdType.INPUT)
  private String traceId;

  @TableField("user_id")
  private Long userId;

  @TableField("origin_key")
  private String originKey;

  @TableField("mode")
  private String mode;

  @TableField("request_snapshot")
  private String requestSnapshot;

  @TableField("request_hash")
  private String requestHash;

  @TableField("status")
  private String status;

  @TableField("order_id")
  private String orderId;

  @TableField("result_json")
  private String resultJson;

  @TableField("failure_code")
  private String failureCode;

  @TableField("failure_reason")
  private String failureReason;

  @TableField("version")
  private Integer version;

  @TableField("expires_at")
  private LocalDateTime expiresAt;

  @TableField("next_check_at")
  private LocalDateTime nextCheckAt;

  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 异步受理的最长处理期限。 */
  @TableField("processing_expires_at")
  private LocalDateTime processingExpiresAt;

  @TableField("accepted_at")
  private LocalDateTime acceptedAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;

  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
