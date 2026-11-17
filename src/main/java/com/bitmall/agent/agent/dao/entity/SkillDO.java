package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/** 数据库中定义的业务办理手册。 包含简短介绍、完整步骤和关联工具列表；加载手册后，相关工具才有机会向模型开放，最终业务约束仍由 Service 校验。 */
public final class SkillDO {
    /** 技能代码，例如 order_create_flow。 */
    private final String code;

    /** 技能展示名称。 */
    private final String name;

    /** 功能简述，帮助调用者判断用途。 */
    private final String description;

    /** 完整办理手册，加载技能时才交给模型。 */
    private final String manual;

    /** 这个技能关联的工具名称列表。 */
    private final List<String> toolNames;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取技能代码，例如 order_create_flow。 */
    public String getCode() {
        return code;
    }

    /** 获取技能展示名称。 */
    public String getName() {
        return name;
    }

    /** 获取功能简述，帮助调用者判断用途。 */
    public String getDescription() {
        return description;
    }

    /** 获取完整办理手册，加载技能时才交给模型。 */
    public String getManual() {
        return manual;
    }

    /** 获取这个技能关联的工具名称列表。 */
    public List<String> getToolNames() {
        return toolNames;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        SkillDO that = (SkillDO) other;
        return Objects.equals(code, that.code)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(manual, that.manual)
                && Objects.equals(toolNames, that.toolNames);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(code, name, description, manual, toolNames);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "SkillDO["
                + "code="
                + code
                + ", name="
                + name
                + ", description="
                + description
                + ", manual="
                + manual
                + ", toolNames="
                + toolNames
                + "]";
    }
}
