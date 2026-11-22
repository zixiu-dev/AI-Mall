package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** pending_action 表的可写持久化对象，不直接作为接口返回。 */
@TableName("pending_action")
@Getter
@Setter
public class PendingActionEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("idempotency_key")
  private String idempotencyKey;

  @TableField("user_id")
  private Long userId;

  @TableField("conversation_id")
  private String conversationId;

  @TableField("tool_name")
  private String toolName;

  @TableField("arguments")
  private String arguments;

  @TableField("arguments_hash")
  private String argumentsHash;

  @TableField("summary")
  private String summary;

  @TableField("status")
  private String status;

  @TableField("result")
  private String result;

  @TableField("version")
  private Integer version;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("decided_at")
  private LocalDateTime decidedAt;

  @TableField("executed_at")
  private LocalDateTime executedAt;
}
