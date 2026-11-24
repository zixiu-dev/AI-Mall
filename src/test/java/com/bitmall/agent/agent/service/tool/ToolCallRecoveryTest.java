package com.bitmall.agent.agent.service.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.skill.SkillService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.ObjectMapper;

/** 故障注入验证参数修正预算、只读退避重试、副作用核对及取消传播。 */
class ToolCallRecoveryTest {
  private final ObjectMapper json = new ObjectMapper();
  private final ToolDispatcher dispatcher = mock(ToolDispatcher.class);
  private final PendingActionService pending = mock(PendingActionService.class);
  private final AgentExecution execution = mock(AgentExecution.class);
  private final ToolExecutionContext context = new ToolExecutionContext(1, "c", "r");
  private final ToolCallRecovery recovery = new ToolCallRecovery(pending, json);

  @BeforeEach
  void setup() {
    when(execution.stage(anyString(), anyInt(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(2)).get());
  }

  private CommerceToolCallback callback(String name, RegisteredTool.Risk risk) {
    var definition = tool(name, "测试", requiredSchema("orderId", "string"), null, risk, (ctx, args) -> null);
    return new CommerceToolCallback(definition, dispatcher, mock(SkillService.class), json,
        new AgentProperties(true, 10, 20, 4000, 12, 180, 32000, 6));
  }

  private String run(CommerceToolCallback callback, String args) {
    return recovery.execute(callback, args, context, Set.of(callback.getName()), execution);
  }

  @Test
  void limitsCorrectionsEvenWhenModelChangesArguments() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    assertThat(run(callback, "{}")).contains("INVALID_ARGUMENT");
    assertThat(run(callback, "{\"orderId\":1}")).contains("INVALID_ARGUMENT");
    assertThat(run(callback, "null")).contains("CORRECTION_LIMIT");
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("CORRECTION_LIMIT");
    verifyNoInteractions(dispatcher);
  }

  @Test
  void repairedArgumentCanExecute() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    when(dispatcher.dispatch(anyString(), any(), anyMap())).thenReturn(Map.of("orderId", "A"));
    assertThat(run(callback, "{}")).contains("INVALID_ARGUMENT");
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("\"ok\":true");
    verify(dispatcher).dispatch(anyString(), any(), anyMap());
  }

  @Test
  void retriesReadTwiceWithBackoffThenReturnsResult() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    when(dispatcher.dispatch(anyString(), any(), anyMap()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"))
        .thenReturn(Map.of("orderId", "A"));
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("\"ok\":true");
    verify(execution).backoff(longThat(v -> v >= 200 && v < 300));
    verify(execution).backoff(longThat(v -> v >= 400 && v < 500));
    verify(dispatcher, times(3)).dispatch(anyString(), any(), anyMap());
  }

  @Test
  void modelCannotRestartExhaustedRetryBudget() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    when(dispatcher.dispatch(anyString(), any(), anyMap()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("private connection"));
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("RETRY_EXHAUSTED").doesNotContain("private connection");
    assertThat(run(callback, "{\"orderId\":\"B\"}")).contains("RETRY_EXHAUSTED");
    verify(dispatcher, times(3)).dispatch(anyString(), any(), anyMap());
  }

  @Test
  void writeAndStatefulReadAreNeverRetried() {
    when(dispatcher.dispatch(anyString(), any(), anyMap()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
    for (String name : Set.of("order_create", "save_preference", "load_skill")) {
      var callback = callback(name, name.equals("order_create") ? RegisteredTool.Risk.WRITE : RegisteredTool.Risk.READ);
      assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("RESULT_UNKNOWN");
      assertThat(run(callback, "{\"orderId\":\"B\"}")).contains("RESULT_UNKNOWN");
      verify(dispatcher).dispatch(eq(name), any(), anyMap());
    }
    verify(execution, never()).backoff(anyLong());
  }

  @Test
  void committedProposalIsRecoveredAfterTimeout() {
    var callback = callback("order_create", RegisteredTool.Risk.WRITE);
    when(dispatcher.dispatch(anyString(), any(), anyMap()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
    var action = mock(com.bitmall.agent.agent.dao.entity.PendingActionDO.class);
    when(action.getId()).thenReturn("original-action");
    when(action.getToolName()).thenReturn("order_create");
    when(action.getSummary()).thenReturn("原确认卡");
    when(pending.latest("c")).thenReturn(action);
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("original-action", "AWAITING_CONFIRMATION");
    run(callback, "{\"orderId\":\"A\"}");
    verify(dispatcher).dispatch(anyString(), any(), anyMap());
  }

  @Test
  void lookupFailurePreservesUnknownAndDoesNotReplay() {
    var callback = callback("order_create", RegisteredTool.Risk.WRITE);
    when(dispatcher.dispatch(anyString(), any(), anyMap())).thenThrow(new RuntimeException("failed"));
    when(pending.latest("c")).thenThrow(new RuntimeException("database down"));
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("RESULT_UNKNOWN");
    verify(execution, never()).backoff(anyLong());
  }

  @Test
  void ownershipRejectionIsNeverCorrectedOrRetried() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    when(dispatcher.dispatch(anyString(), any(), anyMap())).thenThrow(new IllegalArgumentException("订单不属于当前用户"));
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("BUSINESS_REJECTED");
    run(callback, "{\"orderId\":\"B\"}");
    verify(dispatcher).dispatch(anyString(), any(), anyMap());
  }

  @Test
  void cancellationStopsExecution() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    when(dispatcher.dispatch(anyString(), any(), anyMap())).thenThrow(new CancellationException());
    assertThatThrownBy(() -> run(callback, "{\"orderId\":\"A\"}")).isInstanceOf(CancellationException.class);
    verify(execution, never()).backoff(anyLong());
  }

  @Test
  void stageTimeoutRetriesOnlyRead() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    doThrow(new AgentExecution.StageTimeoutException("工具超时"))
        .doReturn("{\"ok\":true,\"data\":{}}")
        .when(execution).stage(anyString(), anyInt(), any());
    assertThat(run(callback, "{\"orderId\":\"A\"}")).contains("\"ok\":true");
    verify(execution).backoff(anyLong());
  }

  @Test
  void permissionsCheckedBeforeBusiness() {
    var callback = callback("order_query", RegisteredTool.Risk.READ);
    assertThat(recovery.execute(callback, "{}", context, Set.of(), execution)).contains("FORBIDDEN");
    verifyNoInteractions(dispatcher);
  }
}
