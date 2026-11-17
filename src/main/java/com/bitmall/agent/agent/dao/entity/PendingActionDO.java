package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** 一笔尚待用户确认或已经处理的操作。 保存原始工具参数、参数校验摘要、操作说明和状态；前端确认时只提交操作编号，后端从这里恢复参数。 这样模型不能在确认时重新拼出另一笔订单。 */
public final class PendingActionDO {
    /** 待确认操作编号，前端确认时只回传这个编号。 */
    private final String id;

    /** 根据请求、工具和参数计算的幂等键，防止同一请求重复生成操作。 */
    private final String idempotencyKey;

    /** 数据所属用户编号，由后端上下文传入。 */
    private final long userId;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 待执行的工具名称。 */
    private final String toolName;

    /** 已经持久化的原始工具参数，确认时从这里恢复。 */
    private final Map<String, Object> arguments;

    /** 工具参数的校验摘要，用于发现确认参数被修改。 */
    private final String argumentsHash;

    /** 供用户阅读的简要说明。 */
    private final String summary;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 操作执行后的业务结果或失败信息。 */
    private final Map<String, Object> result;

    /** 状态版本号，用于条件更新和防重复处理。 */
    private final int version;

    /** 数据创建时间。 */
    private final LocalDateTime createdAt;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public PendingActionDO(
            @JsonProperty("id") String id,
            @JsonProperty("idempotencyKey") String idempotencyKey,
            @JsonProperty("userId") long userId,
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("toolName") String toolName,
            @JsonProperty("arguments") Map<String, Object> arguments,
            @JsonProperty("argumentsHash") String argumentsHash,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") String status,
            @JsonProperty("result") Map<String, Object> result,
            @JsonProperty("version") int version,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.conversationId = conversationId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.argumentsHash = argumentsHash;
        this.summary = summary;
        this.status = status;
        this.result = result;
        this.version = version;
        this.createdAt = createdAt;
    }

    /** 获取待确认操作编号，前端确认时只回传这个编号。 */
    public String getId() {
        return id;
    }

    /** 获取根据请求、工具和参数计算的幂等键，防止同一请求重复生成操作。 */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public long getUserId() {
        return userId;
    }

    /** 获取所属聊天会话编号，用于隔离不同会话的数据。 */
    public String getConversationId() {
        return conversationId;
    }

    /** 获取待执行的工具名称。 */
    public String getToolName() {
        return toolName;
    }

    /** 获取已经持久化的原始工具参数，确认时从这里恢复。 */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /** 获取工具参数的校验摘要，用于发现确认参数被修改。 */
    public String getArgumentsHash() {
        return argumentsHash;
    }

    /** 获取供用户阅读的简要说明。 */
    public String getSummary() {
        return summary;
    }

    /** 获取当前业务状态，由服务端维护。 */
    public String getStatus() {
        return status;
    }

    /** 获取操作执行后的业务结果或失败信息。 */
    public Map<String, Object> getResult() {
        return result;
    }

    /** 获取状态版本号，用于条件更新和防重复处理。 */
    public int getVersion() {
        return version;
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
        PendingActionDO that = (PendingActionDO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(userId, that.userId)
                && Objects.equals(conversationId, that.conversationId)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(arguments, that.arguments)
                && Objects.equals(argumentsHash, that.argumentsHash)
                && Objects.equals(summary, that.summary)
                && Objects.equals(status, that.status)
                && Objects.equals(result, that.result)
                && Objects.equals(version, that.version)
                && Objects.equals(createdAt, that.createdAt);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                idempotencyKey,
                userId,
                conversationId,
                toolName,
                arguments,
                argumentsHash,
                summary,
                status,
                result,
                version,
                createdAt);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "PendingActionDO["
                + "id="
                + id
                + ", idempotencyKey="
                + idempotencyKey
                + ", userId="
                + userId
                + ", conversationId="
                + conversationId
                + ", toolName="
                + toolName
                + ", arguments="
                + arguments
                + ", argumentsHash="
                + argumentsHash
                + ", summary="
                + summary
                + ", status="
                + status
                + ", result="
                + result
                + ", version="
                + version
                + ", createdAt="
                + createdAt
                + "]";
    }
}
