package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次聊天或确认操作完成后返回给前端的完整结果。 既有回复文字，也可能有待确认卡、已加载技能和调试信息。 */
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

    /** 获取真实受理的异步任务编号。 */
    public String getOrderTraceId() {
        return orderTraceId;
    }

    /** 创建附带异步查询编号的新回复，不修改既有回复对象。 */
    public AgentReplyVO withOrderTrace(String traceId) {
        return new AgentReplyVO(
                conversationId, messageId, content, pendingAction, loadedSkills, debug, traceId);
    }

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取所属聊天会话编号，用于隔离不同会话的数据。 */
    public String getConversationId() {
        return conversationId;
    }

    /** 获取本次助手回复写入数据库后生成的消息编号。 */
    public long getMessageId() {
        return messageId;
    }

    /** 获取面向用户展示或写入会话的文本内容。 */
    public String getContent() {
        return content;
    }

    /** 获取需要用户确认的操作卡，没有待确认操作时为空。 */
    public PendingActionVO getPendingAction() {
        return pendingAction;
    }

    /** 获取当前会话中仍有效的已加载技能代码。 */
    public List<String> getLoadedSkills() {
        return loadedSkills;
    }

    /** 获取用于排查问题的模式和工具可见性信息。 */
    public Map<String, Object> getDebug() {
        return debug;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AgentReplyVO that = (AgentReplyVO) other;
        return Objects.equals(conversationId, that.conversationId)
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(content, that.content)
                && Objects.equals(pendingAction, that.pendingAction)
                && Objects.equals(loadedSkills, that.loadedSkills)
                && Objects.equals(debug, that.debug)
                && Objects.equals(orderTraceId, that.orderTraceId);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                conversationId,
                messageId,
                content,
                pendingAction,
                loadedSkills,
                debug,
                orderTraceId);
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
