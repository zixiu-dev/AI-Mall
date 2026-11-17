package com.bitmall.agent.agent.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Objects;

/**
 * 读取 application.yml 中 app.agent 配置。 Spring 通过标有 @ConstructorBinding
 * 的构造方法绑定参数；类中同时明确写出执行次数、超时和上下文大小的默认值。
 */
@ConfigurationProperties(prefix = "app.agent")
public final class AgentProperties {
    /** 是否启用真实模型；关闭时使用本地演示回复。 */
    private final boolean aiEnabled;

    /** 每轮从数据库读取的最近消息条数。 */
    private final int recentMessageLimit;

    /** 消息达到多少条后检查是否需要整理摘要。 */
    private final int summaryTriggerCount;

    /** 普通工具结果允许占用的字符预算。 */
    private final int maxToolResultChars;

    /** 一次 Agent 请求最多进行多少轮推理和工具调用。 */
    private final int maxIterations;

    /** 一次 Agent 请求的总时间上限，单位秒。 */
    private final int requestTimeoutSeconds;

    /** 单次推理上下文允许占用的字符预算。 */
    private final int maxContextChars;

    /** 上下文至少优先保留的最近完整用户轮次数。 */
    private final int keepRecentTurns;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    @ConstructorBinding
    public AgentProperties(
            @JsonProperty("aiEnabled") boolean aiEnabled,
            @JsonProperty("recentMessageLimit") int recentMessageLimit,
            @JsonProperty("summaryTriggerCount") int summaryTriggerCount,
            @JsonProperty("maxToolResultChars") int maxToolResultChars,
            @JsonProperty("maxIterations") int maxIterations,
            @JsonProperty("requestTimeoutSeconds") int requestTimeoutSeconds,
            @JsonProperty("maxContextChars") int maxContextChars,
            @JsonProperty("keepRecentTurns") int keepRecentTurns) {
        maxIterations = maxIterations > 0 ? maxIterations : 12;
        requestTimeoutSeconds = requestTimeoutSeconds > 0 ? requestTimeoutSeconds : 180;
        maxContextChars = maxContextChars > 0 ? maxContextChars : 32000;
        keepRecentTurns = keepRecentTurns > 0 ? keepRecentTurns : 6;
        this.aiEnabled = aiEnabled;
        this.recentMessageLimit = recentMessageLimit;
        this.summaryTriggerCount = summaryTriggerCount;
        this.maxToolResultChars = maxToolResultChars;
        this.maxIterations = maxIterations;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.maxContextChars = maxContextChars;
        this.keepRecentTurns = keepRecentTurns;
    }

    /** 获取是否启用真实模型；关闭时使用本地演示回复。 */
    public boolean isAiEnabled() {
        return aiEnabled;
    }

    /** 获取每轮从数据库读取的最近消息条数。 */
    public int getRecentMessageLimit() {
        return recentMessageLimit;
    }

    /** 获取消息达到多少条后检查是否需要整理摘要。 */
    public int getSummaryTriggerCount() {
        return summaryTriggerCount;
    }

    /** 获取普通工具结果允许占用的字符预算。 */
    public int getMaxToolResultChars() {
        return maxToolResultChars;
    }

    /** 获取一次 Agent 请求最多进行多少轮推理和工具调用。 */
    public int getMaxIterations() {
        return maxIterations;
    }

    /** 获取一次 Agent 请求的总时间上限，单位秒。 */
    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /** 获取单次推理上下文允许占用的字符预算。 */
    public int getMaxContextChars() {
        return maxContextChars;
    }

    /** 获取上下文至少优先保留的最近完整用户轮次数。 */
    public int getKeepRecentTurns() {
        return keepRecentTurns;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AgentProperties that = (AgentProperties) other;
        return Objects.equals(aiEnabled, that.aiEnabled)
                && Objects.equals(recentMessageLimit, that.recentMessageLimit)
                && Objects.equals(summaryTriggerCount, that.summaryTriggerCount)
                && Objects.equals(maxToolResultChars, that.maxToolResultChars)
                && Objects.equals(maxIterations, that.maxIterations)
                && Objects.equals(requestTimeoutSeconds, that.requestTimeoutSeconds)
                && Objects.equals(maxContextChars, that.maxContextChars)
                && Objects.equals(keepRecentTurns, that.keepRecentTurns);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                aiEnabled,
                recentMessageLimit,
                summaryTriggerCount,
                maxToolResultChars,
                maxIterations,
                requestTimeoutSeconds,
                maxContextChars,
                keepRecentTurns);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "AgentProperties["
                + "aiEnabled="
                + aiEnabled
                + ", recentMessageLimit="
                + recentMessageLimit
                + ", summaryTriggerCount="
                + summaryTriggerCount
                + ", maxToolResultChars="
                + maxToolResultChars
                + ", maxIterations="
                + maxIterations
                + ", requestTimeoutSeconds="
                + requestTimeoutSeconds
                + ", maxContextChars="
                + maxContextChars
                + ", keepRecentTurns="
                + keepRecentTurns
                + "]";
    }
}
