package com.bitmall.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

/** 聊天接口的请求参数。 页面发送用户文字和可选会话编号；@NotBlank 要求消息不能只有空白，演示模式省略用户编号时使用 10001。 */
public final class ChatRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 用户本次发送的文字。 */
    @NotBlank private final String message;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public ChatRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("message") String message) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.message = message;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public Long getUserId() {
        return userId;
    }

    /** 获取所属聊天会话编号，用于隔离不同会话的数据。 */
    public String getConversationId() {
        return conversationId;
    }

    /** 获取用户本次发送的文字。 */
    public String getMessage() {
        return message;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ChatRequest that = (ChatRequest) other;
        return Objects.equals(userId, that.userId)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(message, that.message);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(userId, conversationId, message);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ChatRequest["
                + "userId="
                + userId
                + ", conversationId="
                + conversationId
                + ", message="
                + message
                + "]";
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
