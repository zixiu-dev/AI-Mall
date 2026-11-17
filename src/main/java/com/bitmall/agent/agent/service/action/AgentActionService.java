package com.bitmall.agent.agent.service.action;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.chat.AgentReplyService;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.conversation.ConversationSummaryService;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 处理用户点击确认或取消之后的流程。 检查操作归属、获取会话执行权、改变确认状态，再交给 ConfirmedActionExecutor 执行业务并保存回执。 */
@Service
public class AgentActionService {
  /** 外层确认事务回滚后记录对应下单 trace 的失败。 */
  private final com.bitmall.agent.order.service.OrderService orders;

  /** 依赖组件：会话身份和消息访问的业务入口。 */
  private final ConversationService conversations;

  /** 依赖组件：管理待确认操作。 */
  private final PendingActionService pendingActions;

  /** 依赖组件：单进程的会话并发门禁。 */
  private final AgentRunGate runGate;

  /** 依赖组件：确认后真正执行写操作的事务边界。 */
  private final ConfirmedActionExecutor confirmedExecutor;

  /** 依赖组件：整理较早的聊天内容。 */
  private final ConversationSummaryService summaryService;

  /** 依赖组件：组装发给前端的回复和确认文案。 */
  private final AgentReplyService replies;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AgentActionService(
      ConversationService conversations,
      PendingActionService pendingActions,
      AgentRunGate runGate,
      ConfirmedActionExecutor confirmedExecutor,
      ConversationSummaryService summaryService,
      AgentReplyService replies,
      com.bitmall.agent.order.service.OrderService orders) {
    this.orders = orders;
    this.conversations = conversations;
    this.pendingActions = pendingActions;
    this.runGate = runGate;
    this.confirmedExecutor = confirmedExecutor;
    this.summaryService = summaryService;
    this.replies = replies;
  }

  /** 先核对操作归属，再获取会话执行权，避免确认与聊天并发修改同一会话。 */
  public AgentReplyVO confirm(long userId, String actionId, boolean approved) {
    PendingActionDO existing = pendingActions.find(actionId, userId);
    try (AgentRunGate.Lease ignored = runGate.acquire(existing.getConversationId())) {
      return confirmLocked(userId, actionId, approved);
    }
  }

  /** 按用户决定取消或执行；执行成功保存工具回执，失败保存失败状态和可阅读的提示。 */
  private AgentReplyVO confirmLocked(long userId, String actionId, boolean approved) {
    // 1. 条件更新确认状态：同一张卡只能处理一次，重复点击会在这里被拒绝。
    PendingActionDO action = pendingActions.decide(actionId, userId, approved);
    // 2. 用户拒绝只记录决定，不调用订单、领券或售后的写处理器。
    if (!approved) {
      String content = "已取消这次操作，系统没有执行任何业务变更。";
      long messageId =
          conversations.addMessage(
              action.getConversationId(),
              "assistant",
              content,
              Map.of("pendingActionId", action.getId(), "decision", "REJECTED"));
      return replies.create(action.getConversationId(), messageId, content, null);
    }

    try {
      // 3. 只有用户批准才执行。事务在 ConfirmedActionExecutor 中，业务写入和成功标记一起提交。
      Map<String, Object> result = confirmedExecutor.execute(action);
      // 4. 保存真实业务回执，下一轮模型可以读取结果，无需重新猜参数重复执行。
      conversations.addMessage(
          action.getConversationId(),
          "tool",
          replies.writeJson(result),
          Map.of("toolName", action.getToolName(), "pendingActionId", action.getId()));
      String content = replies.renderSuccess(action, result);
      long messageId =
          conversations.addMessage(
              action.getConversationId(),
              "assistant",
              content,
              Map.of("pendingActionId", action.getId(), "decision", "EXECUTED"));
      summaryService.compactIfNeeded(action.getConversationId());
      AgentReplyVO reply = replies.create(action.getConversationId(), messageId, content, null);
      return "order_create".equals(action.getToolName())
              && "PROCESSING".equals(result.get("status"))
          ? reply.withOrderTrace((String) result.get("traceId"))
          : reply;
    } catch (Exception e) {
      // 连接异常或提交后回复写入失败不等于购买失败：保留原 trace，让页面核对结果。
      if ("order_create".equals(action.getToolName())
          && !(e instanceof com.bitmall.agent.order.exception.OrderRejectedException)) {
        var progress = orders.actionProgress(userId, action.getId());
        String content = "本次购买结果需要核对，请使用原购买编号查询，勿重新发起购买。查询编号：" + progress.getTraceId();
        long messageId =
            conversations.addMessage(
                action.getConversationId(),
                "assistant",
                content,
                Map.of("pendingActionId", action.getId(), "decision", "VERIFYING"));
        return replies
            .create(action.getConversationId(), messageId, content, null)
            .withOrderTrace(progress.getTraceId());
      }
      if ("order_create".equals(action.getToolName()))
        orders.failAction(userId, action.getId(), "下单确认失败，请重新结算");
      Map<String, Object> failure = Map.of("success", false, "error", rootMessage(e));
      pendingActions.failed(action, failure);
      String content = "确认已经收到，但执行失败：" + rootMessage(e) + "。请重新查询当前状态后再试。";
      long messageId =
          conversations.addMessage(
              action.getConversationId(),
              "assistant",
              content,
              Map.of("pendingActionId", action.getId(), "decision", "FAILED"));
      return replies.create(action.getConversationId(), messageId, content, null);
    }
  }

  /** 找到异常链中最内层原因，生成给日志和用户提示使用的文字。 */
  private static String rootMessage(Throwable exception) {
    Throwable root = exception;
    while (root.getCause() != null) root = root.getCause();
    return root.getMessage() == null ? "未知错误" : root.getMessage();
  }
}
