package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 发送给页面的 SSE 事件。 start 表示开始，delta 表示追加文字，工具事件显示进度，done 携带最终回复，error 表示本轮失败。 */
public final class StreamEventVO {
    /** 发送给页面的事件类型，例如 start、delta、tool_start、done、error。 */
    private final String type;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    /** 完成事件携带的完整助手回复。 */
    private final AgentReplyVO reply;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public StreamEventVO(
            @JsonProperty("type") String type,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("content") String content,
            @JsonProperty("reply") AgentReplyVO reply) {
        this.type = type;
        this.conversationId = conversationId;
        this.content = content;
        this.reply = reply;
    }

    /** 获取发送给页面的事件类型，例如 start、delta、tool_start、done、error。 */
    public String getType() {
        return type;
    }

    /** 获取所属聊天会话编号，用于隔离不同会话的数据。 */
    public String getConversationId() {
        return conversationId;
    }

    /** 获取面向用户展示或写入会话的文本内容。 */
    public String getContent() {
        return content;
    }

    /** 获取完成事件携带的完整助手回复。 */
    public AgentReplyVO getReply() {
        return reply;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        StreamEventVO that = (StreamEventVO) other;
        return Objects.equals(type, that.type)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(content, that.content)
                && Objects.equals(reply, that.reply);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(type, conversationId, content, reply);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "StreamEventVO["
                + "type="
                + type
                + ", conversationId="
                + conversationId
                + ", content="
                + content
                + ", reply="
                + reply
                + "]";
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
