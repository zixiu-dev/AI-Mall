package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 一条聊天消息的数据对象，对应 chat_message 表的一行。 ConversationMapper 查询数据库后组装它，Service 再读取角色、正文和附加信息。
 * 它只承载数据，不调用模型，也不执行 SQL；通过 getContent()、getRole() 等普通 Getter 读取字段。
 */
@Getter
@EqualsAndHashCode
@ToString
public final class ChatMessageDO {
    /** 消息编号，对应 chat_message 表主键。 */
    private final long id;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 消息角色：user 表示用户，assistant 表示助手，tool 表示工具。 */
    private final String role;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    /** 消息附加信息，例如待确认操作编号、执行决定。 */
    private final Map<String, Object> metadata;

    private final LocalDateTime createdAt;

    @JsonCreator
    public ChatMessageDO(
            @JsonProperty("id") long id,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }
}
