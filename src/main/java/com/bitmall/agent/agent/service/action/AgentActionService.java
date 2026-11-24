package com.bitmall.agent.agent.service.action;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.chat.AgentReplyService;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.conversation.ConversationSummaryService;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.order.vo.OrderSubmitVO;

/** 处理用户点击确认或取消之后的流程。 检查操作归属、获取会话执行权、改变确认状态，再交给 ConfirmedActionExecutor 执行业务并保存回执。 */
@Service
@RequiredArgsConstructor
public class AgentActionService {
  /** 把确认/取消的执行结果作为助手消息写入会话。 */
  private final ConversationService conversations;

  /** 读取待确认操作，记录用户的确认/取消决定和执行失败结果。 */
  private final PendingActionService pendingActions;

  /** 单进程的会话并发门禁。 */
  private final AgentRunGate runGate;

  /** 执行已获用户批准的业务操作，隔离执行事务并记录成功或失败。 */
  private final ConfirmedActionExecutor confirmedExecutor;

  /** 写入回执消息后，历史过长时压缩较早轮次为摘要。 */
  private final ConversationSummaryService summaryService;

  /** 把回复、调试信息和真实确认卡组装成前端返回对象。 */
  private final AgentReplyService replies;

  /** 下单确认执行出错时：结果不确定就查询购买进度、提示用户用原编号核对；明确失败则把这次购买记为失败。 */
  private final OrderService orders;

  /** 先核对操作归属，再获取会话执行权，避免确认与聊天并发修改同一会话。 */
  public AgentReplyVO confirm(long userId, String actionId, boolean approved) {
    PendingActionDO existing = pendingActions.find(actionId, userId);
    try (AgentRunGate.Lease ignored = runGate.acquire(existing.getConversationId())) {
      return confirmLocked(userId, actionId, approved);
    }
  }

  /** 按用户决定取消或执行；执行成功保存工具回执，失败保存失败状态和可阅读的提示。 */
  private AgentReplyVO confirmLocked(long userId, String actionId, boolean approved) {
    // 已处理的确认请求只读取原结果；跨进程竞争仍由 decide 的数据库 CAS 决定唯一执行者。
    PendingActionDO stored = pendingActions.find(actionId, userId);
    if (!"PENDING".equals(stored.getStatus())) return recoveredReply(stored);
    PendingActionDO action;
    try {
      action = pendingActions.decide(actionId, userId, approved);
    } catch (RuntimeException conflict) {
      stored = pendingActions.find(actionId, userId);
      if (!"PENDING".equals(stored.getStatus())) return recoveredReply(stored);
      throw conflict;
    }
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
      // 先核对持久化结果。业务已提交而聊天写入失败时，返回已有成功回执。
      PendingActionDO current;
      try {
        current = pendingActions.find(actionId, userId);
      } catch (RuntimeException unavailable) {
        return verifyingReply(action);
      }
      if ("EXECUTED".equals(current.getStatus()) || "FAILED".equals(current.getStatus())) {
        return recoveredReply(current);
      }
      if (e instanceof OrderRejectedException || e instanceof IllegalArgumentException
          || e instanceof com.bitmall.agent.common.exception.BusinessRejectedException) {
        if ("order_create".equals(action.getToolName())) {
          orders.failAction(userId, action.getId(), "下单确认失败，请重新结算");
        }
        Map<String, Object> failure = Map.of("success", false, "error", rootMessage(e));
        pendingActions.failed(action, failure);
        return recoveredReply(pendingActions.find(actionId, userId));
      }
      // 网络、提交异常和未知程序故障保留 APPROVED，后续沿用 actionId 核对。
      return verifyingReply(current);
    }
  }

  /** 重复确认返回原业务结果，避免再次调用写处理器和重复写聊天回执。 */
  private AgentReplyVO recoveredReply(PendingActionDO action) {
    if ("APPROVED".equals(action.getStatus())) return verifyingReply(action);
    String content = switch (action.getStatus()) {
      case "EXECUTED" -> replies.renderSuccess(action, action.getResult());
      case "REJECTED" -> "这次操作已取消。";
      case "FAILED" -> "这次操作执行失败：" + action.getResult().getOrDefault("error", "请核对业务状态");
      default -> "请核对原操作状态，操作编号：" + action.getId();
    };
    AgentReplyVO reply = recoveryResponse(action, content);
    if ("order_create".equals(action.getToolName()) && action.getResult().get("traceId") instanceof String trace) {
      return reply.withOrderTrace(trace);
    }
    return reply;
  }

  /** 下单查询原 trace，其他操作保留原 actionId；查询服务故障也不能触发重新执行。 */
  private AgentReplyVO verifyingReply(PendingActionDO action) {
    String content = "操作正在处理或结果待核对，请使用原操作编号重试查询：" + action.getId();
    if ("order_create".equals(action.getToolName())) {
      try {
        OrderSubmitVO progress = orders.actionProgress(action.getUserId(), action.getId());
        content = "请按原购买编号查询结果：" + progress.getTraceId() + "，当前状态：" + progress.getStatus();
        return recoveryResponse(action, content).withOrderTrace(progress.getTraceId());
      } catch (RuntimeException unavailable) {
        // 保留可重查的确认编号，不能把查询异常当成购买失败。
      }
    }
    return recoveryResponse(action, content);
  }

  /** 恢复响应直接使用已读取的记录，避免因聊天写库故障再次损坏业务结果。 */
  private AgentReplyVO recoveryResponse(PendingActionDO action, String content) {
    return new AgentReplyVO(action.getConversationId(), 0, content, null, java.util.List.of(),
        Map.of("pendingActionId", action.getId(), "status", action.getStatus(), "recovered", true));
  }

  /** 找到异常链中最内层原因，生成给日志和用户提示使用的文字。 */
  private static String rootMessage(Throwable exception) {
    Throwable root = exception;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage() == null ? "未知错误" : root.getMessage();
  }
}
