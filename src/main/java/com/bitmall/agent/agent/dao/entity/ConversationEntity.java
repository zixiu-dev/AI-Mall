package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** conversation 表的可写持久化对象，不直接作为接口返回。 */
@TableName("conversation")
@Getter
@Setter
public class ConversationEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("user_id")
  private Long userId;

  @TableField("title")
  private String title;

  @TableField("summary")
  private String summary;

  @TableField("summarized_until_id")
  private Long summarizedUntilId;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
