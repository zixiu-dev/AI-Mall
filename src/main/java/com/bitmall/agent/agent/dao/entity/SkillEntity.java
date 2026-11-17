package com.bitmall.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** agent_skill 表的可写持久化对象，不直接作为接口返回。 */
@TableName("agent_skill")
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

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getManual() {
    return manual;
  }

  public void setManual(String manual) {
    this.manual = manual;
  }

  public String getToolNames() {
    return toolNames;
  }

  public void setToolNames(String toolNames) {
    this.toolNames = toolNames;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
}
