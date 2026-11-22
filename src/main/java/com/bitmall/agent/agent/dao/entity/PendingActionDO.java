package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 一笔尚待用户确认或已经处理的操作。 保存原始工具参数、参数校验摘要、操作说明和状态；前端确认时只提交操作编号，后端从这里恢复参数。 这样模型不能在确认时重新拼出另一笔订单。 */
@Getter
@EqualsAndHashCode
@ToString
public final class PendingActionDO {
    /** 待确认操作编号，前端确认时只回传这个编号。 */
    private final String id;

    /** 根据请求、工具和参数计算的幂等键，防止同一请求重复生成操作。 */
    private final String idempotencyKey;

    /** 数据所属用户编号，由后端上下文传入。 */
    private final long userId;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    private final String toolName;

    /** 已经持久化的原始工具参数，确认时从这里恢复。 */
    private final Map<String, Object> arguments;

    /** 工具参数的校验摘要，用于发现确认参数被修改。 */
    private final String argumentsHash;

    private final String summary;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 操作执行后的业务结果或失败信息。 */
    private final Map<String, Object> result;

    /** 状态版本号，用于条件更新和防重复处理。 */
    private final int version;

    private final LocalDateTime createdAt;

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
}
