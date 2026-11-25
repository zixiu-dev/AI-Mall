package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.trace.AgentTurnTrace;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 在 Agent 内部执行模型请求的工具，负责失败恢复和单轮诊断。 */
@Component
@RequiredArgsConstructor
public class ToolExecutor {
  private static final String TOOL_UNAVAILABLE_RESULT =
      "{\"ok\":false,\"error\":\"工具尚未开放或不存在，请先加载对应技能手册\"}";

  /** 处理参数错误、只读重试和副作用结果核对。 */
  private final ToolCallRecovery toolRecovery;

  /** 从结构化工具回执提取诊断状态和确认卡编号。 */
  private final ObjectMapper json;

  /** 执行并返回原工具回执，由主循环决定如何保存和送回模型。 */
  public String execute(AssistantMessage.ToolCall call, List<ToolCallback> callbacks, Set<String> allowed,
      ToolExecutionContext context, AgentExecution execution, int stepNo) {
    ToolCallback callback = findCallback(callbacks, call.name());
    AgentTurnTrace.Step step = execution.traceStep(stepNo);
    AgentTurnTrace.Entry trace = step == null ? null
        : step.startTool(call.id(), callback == null ? null : call.name());
    return runTool(context, call, callback, allowed, execution, trace);
  }

  /** 一次模型工具请求只产生一条明细。回执正文仍由历史保存，诊断表只取状态和原卡编号。 */
  private String runTool(
      ToolExecutionContext context, AssistantMessage.ToolCall call, ToolCallback callback, Set<String> allowed,
      AgentExecution execution, AgentTurnTrace.Entry trace) {
    if (callback == null) {
      if (trace != null) trace.finish("REJECTED", "TOOL_UNAVAILABLE");
      return TOOL_UNAVAILABLE_RESULT;
    }
    try {
      String result = toolRecovery.execute(callback, call.arguments(), context, allowed, execution,
          trace == null ? null : trace::markExecutionStarted);
      finishToolTrace(trace, result);
      return result;
    } catch (CancellationException cancelled) {
      if (trace != null) trace.finish("CANCELLED", null);
      throw cancelled;
    } catch (RuntimeException failure) {
      if (trace != null) {
        String code = failure instanceof AgentExecution.RequestTimeoutException ? "TURN_TIMEOUT" : "TOOL_FAILED";
        trace.finish("FAILED", code);
      }
      throw failure;
    }
  }

  private void finishToolTrace(AgentTurnTrace.Entry trace, String result) {
    if (trace == null) {
      return;
    }
    try {
      var response = json.readTree(result);
      if (response.path("ok").asBoolean()) {
        var data = response.path("data");
        if ("AWAITING_CONFIRMATION".equals(data.path("status").asText())) {
          trace.actionId(data.path("pendingActionId").asText()).finish("AWAITING_CONFIRMATION", null);
        } else {
          trace.finish("SUCCESS", null);
        }
      } else {
        String code = response.path("code").asText();
        trace.finish("FAILED", code.matches("[A-Z0-9_]{1,64}") ? code : "TOOL_FAILED");
      }
    } catch (RuntimeException invalidReply) {
      trace.finish("FAILED", "UNREADABLE_TOOL_RESULT");
    }
  }

  private static ToolCallback findCallback(List<ToolCallback> callbacks, String toolName) {
    return callbacks.stream()
        .filter(callback -> callback.getToolDefinition().name().equals(toolName))
        .findFirst()
        .orElse(null);
  }
}
