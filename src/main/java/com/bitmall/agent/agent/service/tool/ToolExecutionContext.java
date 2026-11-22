package com.bitmall.agent.agent.service.tool;

import lombok.Getter;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** 后端本轮工具调用的身份信息。 用户、会话和请求编号由编排流程设置，不让模型通过工具 JSON 参数自行指定另一个用户；演示接口本身尚未接登录态。 */
@Getter
public final class ToolExecutionContext {
  /** 数据所属用户编号，由后端上下文传入。 */
  private final long userId;

  /** 所属聊天会话编号，用于隔离不同会话的数据。 */
  private final String conversationId;

  /** 本轮请求编号，用于追踪调用和生成幂等键。 */
  private final String requestId;

  /** 仅活跃请求使用的原文、历史与预算，不随确认卡持久化。 */
  @JsonIgnore private final KnowledgeExecutionContext knowledgeContext;

  /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
  @JsonCreator
  public ToolExecutionContext(
      @JsonProperty("userId") long userId,
      @JsonProperty("conversationId") String conversationId,
      @JsonProperty("requestId") String requestId) {
    this(userId, conversationId, requestId, null);
  }

  public ToolExecutionContext(
      long userId,
      String conversationId,
      String requestId,
      KnowledgeExecutionContext knowledgeContext) {
    this.knowledgeContext = knowledgeContext;
    this.userId = userId;
    this.conversationId = conversationId;
    this.requestId = requestId;
  }

  @JsonIgnore
  public KnowledgeExecutionContext getKnowledgeContext() {
    return knowledgeContext;
  }

  /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ToolExecutionContext that = (ToolExecutionContext) other;
    return Objects.equals(userId, that.userId)
        && Objects.equals(conversationId, that.conversationId)
        && Objects.equals(requestId, that.requestId);
  }

  /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
  @Override
  public int hashCode() {
    return Objects.hash(userId, conversationId, requestId);
  }

  /** 返回字段摘要，供日志和本地调试查看。 */
  @Override
  public String toString() {
    return "ToolExecutionContext["
        + "userId="
        + userId
        + ", conversationId="
        + conversationId
        + ", requestId="
        + requestId
        + "]";
  }
}
