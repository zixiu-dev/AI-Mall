package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** user_memory 表的可写持久化对象，不直接作为接口返回。 */
@TableName("user_memory")
@Getter
@Setter
public class MemoryEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("user_id")
  private Long userId;

  @TableField("memory_key")
  private String memoryKey;

  @TableField("memory_value")
  private String memoryValue;

  @TableField("source_conversation_id")
  private String sourceConversationId;

  @TableField("version")
  private Integer version;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
