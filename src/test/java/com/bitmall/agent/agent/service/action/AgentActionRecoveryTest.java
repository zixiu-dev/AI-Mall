package com.bitmall.agent.agent.service.action;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.chat.AgentReplyService;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.conversation.ConversationSummaryService;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.order.service.OrderService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 写操作提交与回复失败的边界测试，验证重复确认始终复用原记录。 */
class AgentActionRecoveryTest {
  private final PendingActionService pending = mock(PendingActionService.class);
  private final ConfirmedActionExecutor executor = mock(ConfirmedActionExecutor.class);
  private final ConversationService conversations = mock(ConversationService.class);
  private final AgentReplyService replies = mock(AgentReplyService.class);
  private final OrderService orders = mock(OrderService.class);
  private final AgentActionService service = new AgentActionService(conversations, pending,
      new AgentRunGate(), executor, mock(ConversationSummaryService.class), replies, orders);

  private PendingActionDO action(String status) {
    var action = mock(PendingActionDO.class);
    when(action.getId()).thenReturn("a");
    when(action.getUserId()).thenReturn(1L);
    when(action.getConversationId()).thenReturn("c");
    when(action.getToolName()).thenReturn("coupon_claim");
    when(action.getStatus()).thenReturn(status);
    when(action.getResult()).thenReturn(Map.of("name", "优惠券"));
    return action;
  }

  @Test
  void repeatedConfirmationReturnsCommittedResultWithoutWritingAgain() {
    var done = action("EXECUTED");
    when(pending.find("a", 1)).thenReturn(done);
    when(replies.renderSuccess(done, done.getResult())).thenReturn("优惠券领取成功");
    assertThat(service.confirm(1, "a", true).getContent()).contains("领取成功");
    verifyNoInteractions(executor);
    verify(pending, never()).decide(anyString(), anyLong(), anyBoolean());
  }

  @Test
  void businessCommittedButChatWriteFailedStillReturnsSuccess() {
    var before = action("PENDING");
    var approved = action("APPROVED");
    var done = action("EXECUTED");
    when(pending.find("a", 1)).thenReturn(before, before, done);
    when(pending.decide("a", 1, true)).thenReturn(approved);
    Map<String, Object> committed = done.getResult();
    when(executor.execute(approved)).thenReturn(committed);
    when(conversations.addMessage(any(), any(), any(), anyMap())).thenThrow(new IllegalStateException("chat failed"));
    when(replies.renderSuccess(done, done.getResult())).thenReturn("领取成功");
    assertThat(service.confirm(1, "a", true).getContent()).contains("领取成功");
    verify(pending, never()).failed(any(), anyMap());
    verify(executor).execute(approved);
  }

  @Test
  void unknownWriteResultDoesNotMarkFailedOrReplay() {
    var approved = action("APPROVED");
    when(pending.find("a", 1)).thenReturn(approved);
    assertThat(service.confirm(1, "a", true).getContent()).contains("待核对", "a");
    verifyNoInteractions(executor);
    verify(pending, never()).failed(any(), anyMap());
  }

  @Test
  void orderStatusQueryFailurePreservesOriginalActionId() {
    var approved = action("APPROVED");
    when(approved.getToolName()).thenReturn("order_create");
    when(pending.find("a", 1)).thenReturn(approved);
    when(orders.actionProgress(1, "a")).thenThrow(new RuntimeException("db unavailable"));
    assertThat(service.confirm(1, "a", true).getDebug()).containsEntry("pendingActionId", "a");
    verifyNoInteractions(executor);
  }
}
