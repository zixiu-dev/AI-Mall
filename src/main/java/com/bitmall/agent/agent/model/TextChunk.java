package com.bitmall.agent.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Agent 内部流转的文本片段或工具进度。 reset 表示清空之前的临时文字，delta 表示追加；AgentChatService 再把它转换成前端 SSE 事件。 */
public final class TextChunk {
    /** 内部文本片段类型：delta、reset 或工具进度事件。 */
    private final String type;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public TextChunk(@JsonProperty("type") String type, @JsonProperty("content") String content) {
        this.type = type;
        this.content = content;
    }

    /** 获取内部文本片段类型：delta、reset 或工具进度事件。 */
    public String getType() {
        return type;
    }

    /** 获取面向用户展示或写入会话的文本内容。 */
    public String getContent() {
        return content;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        TextChunk that = (TextChunk) other;
        return Objects.equals(type, that.type) && Objects.equals(content, that.content);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(type, content);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "TextChunk[" + "type=" + type + ", content=" + content + "]";
    }

    /** 创建内部追加文字片段，之后会转换成 SSE delta 事件。 */
    public static TextChunk delta(String content) {
        return new TextChunk("delta", content);
    }

    /** 创建内部重置片段，通知编排清空此前收集的临时回复。 */
    public static TextChunk reset() {
        return new TextChunk("reset", null);
    }
}
