package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 工具执行恢复策略。参数最多修正两次，纯查询最多重试两次，副作用工具先核对确认卡。 */
@Component
@RequiredArgsConstructor
public class ToolCallRecovery {
  /** 查询已经落库的确认卡，恢复超时后丢失的提议结果。 */
  private final PendingActionService pending;
  /** 读写工具协议，避免依赖错误文案猜测是否可重试。 */
  private final ObjectMapper json;

  public String execute(ToolCallback callback, String arguments, ToolExecutionContext context,
      Set<String> allowed, AgentExecution execution) {
    String name = callback.getToolDefinition().name();
    ToolFailureBudget budget = context.getFailureBudget();
    String stopped = budget.stopped(name);
    if (stopped != null) return stopped;
    boolean readOnly = callback instanceof CommerceToolCallback commerce && commerce.isReadOnly();
    ToolContext toolContext = new ToolContext(Map.of(
        ToolExecutionContext.class.getName(), context, SkillContextPolicy.ALLOWED_TOOLS, allowed));

    while (true) {
      execution.check();
      String result;
      try {
        result = execution.stage("工具执行", 15, () -> callback.call(arguments, toolContext));
      } catch (CancellationException e) {
        throw e;
      } catch (RuntimeException e) {
        execution.check();
        boolean transientError = e instanceof AgentExecution.StageTimeoutException || ToolFailure.isTransient(e);
        String code = !readOnly ? "RESULT_UNKNOWN" : transientError ? "TRANSIENT_ERROR" : "EXECUTION_FAILED";
        result = ToolFailure.result(json, code, "工具执行失败或超时，请核对当前状态",
            readOnly ? "STOP" : "QUERY_ORIGINAL_OPERATION");
      }
      execution.check();
      var response = json.readTree(result);
      if (response.path("ok").asBoolean()) return result;
      String code = response.path("code").asText();
      if ("INVALID_ARGUMENT".equals(code)) {
        if (budget.invalid(name) < 3) return result;
        result = ToolFailure.result(json, "CORRECTION_LIMIT", "参数修正次数已用尽："
            + response.path("error").asText(), "ASK_USER");
      } else if ("TRANSIENT_ERROR".equals(code) && readOnly) {
        int retry = budget.retry(name);
        if (retry <= 2) {
          execution.backoff(200L * (1L << (retry - 1)) + ThreadLocalRandom.current().nextLong(100));
          continue;
        }
        result = ToolFailure.result(json, "RETRY_EXHAUSTED", "查询服务暂时不可用，重试次数已用尽", "STOP");
      } else if ("RESULT_UNKNOWN".equals(code)) {
        result = recoverPending(context, result);
      }
      // 模型再次用新调用 ID 发起相同工具，也只能得到原失败或恢复回执。
      budget.stop(name, result);
      return result;
    }
  }

  /** 查询失败也维持结果未知，不因为查不到确认卡就重复执行副作用。 */
  private String recoverPending(ToolExecutionContext context, String unknown) {
    try {
      var action = pending.latest(context.getConversationId());
      if (action != null) {
        return json.writeValueAsString(Map.of("ok", true, "data", Map.of(
            "status", "AWAITING_CONFIRMATION", "pendingActionId", action.getId(),
            "toolName", action.getToolName(), "summary", action.getSummary(),
            "message", "已查到本会话原确认卡，请核对后处理")));
      }
    } catch (RuntimeException ignored) {
      // 核对存储不可用时保留原错误，后续用户通过原会话/确认编号继续查询。
    }
    return unknown;
  }
}
