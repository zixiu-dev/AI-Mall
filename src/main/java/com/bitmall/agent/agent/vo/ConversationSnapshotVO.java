package com.bitmall.agent.agent.vo;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 页面恢复所需的近期聊天、当前确认卡和已关联购买进度。 */
@Getter
@RequiredArgsConstructor
public final class ConversationSnapshotVO {
  private final String conversationId;
  private final List<ChatMessageDO> messages;
  private final PendingActionVO pendingAction;
  private final List<OrderSubmitVO> orderProgress;
}
