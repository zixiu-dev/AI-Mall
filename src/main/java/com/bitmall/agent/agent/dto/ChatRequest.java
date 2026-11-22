package com.bitmall.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 聊天接口的请求参数。 页面发送用户文字和可选会话编号；@NotBlank 要求消息不能只有空白，演示模式省略用户编号时使用 10001。 */
@Getter
@EqualsAndHashCode
@ToString
public final class ChatRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    @NotBlank private final String message;

    @JsonCreator
    public ChatRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("message") String message) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.message = message;
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
