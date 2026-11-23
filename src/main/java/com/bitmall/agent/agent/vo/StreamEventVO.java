package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 发送给页面的 SSE 事件。 start 表示开始，delta 表示追加文字，工具事件显示进度，done 携带最终回复，error 表示本轮失败。 */
@Getter
@EqualsAndHashCode
@ToString
public final class StreamEventVO {
    /** 发送给页面的事件类型，例如 start、delta、tool_start、done、error。 */
    private final String type;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    /** 完成事件携带的完整助手回复。 */
    private final AgentReplyVO reply;

    /** 可重连流中的草稿版本，旧聊天入口的事件省略该字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long revision;

    @JsonCreator
    public StreamEventVO(
            @JsonProperty("type") String type,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("content") String content,
            @JsonProperty("reply") AgentReplyVO reply,
            @JsonProperty("revision") Long revision) {
        this.type = type;
        this.conversationId = conversationId;
        this.content = content;
        this.reply = reply;
        this.revision = revision;
    }

    public StreamEventVO(String type, String conversationId, String content, AgentReplyVO reply) {
        this(type, conversationId, content, reply, null);
    }

    /** 创建会话开始事件，让页面知道这一轮归属哪个会话。 */
    public static StreamEventVO start(String conversationId) {
        return new StreamEventVO("start", conversationId, null, null);
    }

    /** 创建追加文字事件，页面在现有回复后继续拼接。 */
    public static StreamEventVO delta(String conversationId, String content) {
        return new StreamEventVO("delta", conversationId, content, null);
    }

    /** 创建清空临时回复事件，后续文字将替代已经显示的草稿。 */
    public static StreamEventVO reset(String conversationId) {
        return new StreamEventVO("reset", conversationId, null, null);
    }

    /** 创建完成事件，携带已经落库的最终回复和确认卡。 */
    public static StreamEventVO done(AgentReplyVO reply) {
        return new StreamEventVO("done", reply.getConversationId(), null, reply);
    }

    /** 创建失败事件，告诉页面本轮未正常完成。 */
    public static StreamEventVO error(String conversationId, String content) {
        return new StreamEventVO("error", conversationId, content, null);
    }
}
