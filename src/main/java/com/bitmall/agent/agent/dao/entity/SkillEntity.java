package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

/** agent_skill 表的可写持久化对象，不直接作为接口返回。 */
@TableName("agent_skill")
@Getter
@Setter
public class SkillEntity {
  @TableId(value = "code", type = IdType.INPUT)
  private String code;

  @TableField("name")
  private String name;

  @TableField("description")
  private String description;

  @TableField("manual")
  private String manual;

  @TableField("tool_names")
  private String toolNames;

  @TableField("enabled")
  private Boolean enabled;
}
