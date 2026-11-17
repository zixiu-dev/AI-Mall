package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 一条聊天消息的数据对象，对应 chat_message 表的一行。 ConversationMapper 查询数据库后组装它，Service 再读取角色、正文和附加信息。
 * 它只承载数据，不调用模型，也不执行 SQL；通过 getContent()、getRole() 等普通 Getter 读取字段。
 */
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

    /** 数据创建时间。 */
    private final LocalDateTime createdAt;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取消息编号，对应 chat_message 表主键。 */
    public long getId() {
        return id;
    }

    /** 获取所属聊天会话编号，用于隔离不同会话的数据。 */
    public String getConversationId() {
        return conversationId;
    }

    /** 获取消息角色：user 表示用户，assistant 表示助手，tool 表示工具。 */
    public String getRole() {
        return role;
    }

    /** 获取面向用户展示或写入会话的文本内容。 */
    public String getContent() {
        return content;
    }

    /** 获取消息附加信息，例如待确认操作编号、执行决定。 */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /** 获取数据创建时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ChatMessageDO that = (ChatMessageDO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(role, that.role)
                && Objects.equals(content, that.content)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(createdAt, that.createdAt);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(id, conversationId, role, content, metadata, createdAt);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ChatMessageDO["
                + "id="
                + id
                + ", conversationId="
                + conversationId
                + ", role="
                + role
                + ", content="
                + content
                + ", metadata="
                + metadata
                + ", createdAt="
                + createdAt
                + "]";
    }
}
