package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 数据库中定义的业务办理手册。 包含简短介绍、完整步骤和关联工具列表；加载手册后，相关工具才有机会向模型开放，最终业务约束仍由 Service 校验。 */
@Getter
@EqualsAndHashCode
@ToString
public final class SkillDO {
    /** 技能代码，例如 order_create_flow。 */
    private final String code;

    private final String name;

    /** 功能简述，帮助调用者判断用途。 */
    private final String description;

    /** 完整办理手册，加载技能时才交给模型。 */
    private final String manual;

    private final List<String> toolNames;

    @JsonCreator
    public SkillDO(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("manual") String manual,
            @JsonProperty("toolNames") List<String> toolNames) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.manual = manual;
        this.toolNames = toolNames;
    }
}
