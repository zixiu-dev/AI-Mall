package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.vo.ConversationSnapshotVO;
import com.bitmall.agent.agent.vo.PendingActionVO;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 读取页面恢复快照，按后端确认和购买记录恢复实际状态。 */
@Service
@RequiredArgsConstructor
public class ConversationSnapshotService {
  private static final int HISTORY_LIMIT = 100;
  private static final int ORDER_LIMIT = 20;

  /** 校验归属并读取会话消息。 */
  private final ConversationService conversations;

  /** 读取当前待确认动作和近期下单操作编号。 */
  private final PendingActionService actions;

  /** 按操作来源号查询原购买进度，复用订单业务状态。 */
  private final OrderService orders;

  public ConversationSnapshotVO get(long userId, String conversationId) {
    conversations.requireOwned(userId, conversationId);
    List<ChatMessageDO> messages =
        conversations.recentMessages(conversationId, HISTORY_LIMIT).stream()
            .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
            .toList();
    PendingActionDO pending = actions.latest(conversationId);
    PendingActionVO card = pending == null ? null
        : new PendingActionVO(pending.getId(), pending.getToolName(), pending.getSummary());
    List<OrderSubmitVO> progress = actions.recentDecidedOrderIds(userId, conversationId, ORDER_LIMIT)
        .stream()
        .map(actionId -> orders.findActionProgress(userId, actionId))
        .flatMap(Optional::stream)
        .toList();
    return new ConversationSnapshotVO(conversationId, messages, card, progress);
  }
}
