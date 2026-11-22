package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 一次聊天或确认操作完成后返回给前端的完整结果。 既有回复文字，也可能有待确认卡、已加载技能和调试信息。 */
@Getter
@EqualsAndHashCode
public final class AgentReplyVO {
    /** 异步下单查询编号，只有真实受理后才返回，页面可继续查询进度。 */
    private final String orderTraceId;

    /** 所属聊天会话编号，用于隔离不同会话的数据。 */
    private final String conversationId;

    /** 本次助手回复写入数据库后生成的消息编号。 */
    private final long messageId;

    /** 面向用户展示或写入会话的文本内容。 */
    private final String content;

    /** 需要用户确认的操作卡，没有待确认操作时为空。 */
    private final PendingActionVO pendingAction;

    /** 当前会话中仍有效的已加载技能代码。 */
    private final List<String> loadedSkills;

    /** 用于排查问题的模式和工具可见性信息。 */
    private final Map<String, Object> debug;

    /** 兼容普通聊天回复，默认没有异步下单任务。 */
    public AgentReplyVO(
            String conversationId,
            long messageId,
            String content,
            PendingActionVO pendingAction,
            List<String> loadedSkills,
            Map<String, Object> debug) {
        this(conversationId, messageId, content, pendingAction, loadedSkills, debug, null);
    }

    /** 创建附带异步查询编号的新回复，不修改既有回复对象。 */
    public AgentReplyVO withOrderTrace(String traceId) {
        return new AgentReplyVO(
                conversationId, messageId, content, pendingAction, loadedSkills, debug, traceId);
    }

    @JsonCreator
    public AgentReplyVO(
            @JsonProperty("conversationId") String conversationId,
            @JsonProperty("messageId") long messageId,
            @JsonProperty("content") String content,
            @JsonProperty("pendingAction") PendingActionVO pendingAction,
            @JsonProperty("loadedSkills") List<String> loadedSkills,
            @JsonProperty("debug") Map<String, Object> debug,
            @JsonProperty("orderTraceId") String orderTraceId) {
        this.orderTraceId = orderTraceId;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.content = content;
        this.pendingAction = pendingAction;
        this.loadedSkills = loadedSkills;
        this.debug = debug;
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "AgentReplyVO["
                + "conversationId="
                + conversationId
                + ", messageId="
                + messageId
                + ", content="
                + content
                + ", pendingAction="
                + pendingAction
                + ", loadedSkills="
                + loadedSkills
                + ", debug="
                + debug
                + "]";
    }
}
