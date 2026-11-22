package com.bitmall.agent.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Agent 内部流转的文本片段或工具进度。 reset 表示清空之前的临时文字，delta 表示追加；AgentChatService 再把它转换成前端 SSE 事件。 */
@Getter
@EqualsAndHashCode
@ToString
public final class TextChunk {
    /** 内部文本片段类型：delta、reset 或工具进度事件。 */
    private final String type;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    @JsonCreator
    public TextChunk(@JsonProperty("type") String type, @JsonProperty("content") String content) {
        this.type = type;
        this.content = content;
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
