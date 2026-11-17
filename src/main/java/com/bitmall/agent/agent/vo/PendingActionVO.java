package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 前端绘制确认卡需要的少量信息。 只暴露操作编号、工具名称和摘要，真正执行的参数保存在后端 PendingActionDO 中。 */
public final class PendingActionVO {
    /** 这条数据的唯一编号。 */
    private final String id;

    /** 待执行的工具名称。 */
    private final String toolName;

    /** 供用户阅读的简要说明。 */
    private final String summary;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public PendingActionVO(
            @JsonProperty("id") String id,
            @JsonProperty("toolName") String toolName,
            @JsonProperty("summary") String summary) {
        this.id = id;
        this.toolName = toolName;
        this.summary = summary;
    }

    /** 获取这条数据的唯一编号。 */
    public String getId() {
        return id;
    }

    /** 获取待执行的工具名称。 */
    public String getToolName() {
        return toolName;
    }

    /** 获取供用户阅读的简要说明。 */
    public String getSummary() {
        return summary;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PendingActionVO that = (PendingActionVO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(summary, that.summary);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(id, toolName, summary);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "PendingActionVO["
                + "id="
                + id
                + ", toolName="
                + toolName
                + ", summary="
                + summary
                + "]";
    }
}
