package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.skill.SkillService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import com.bitmall.agent.order.service.OrderService;

/** 工具调用的统一分发入口。 先检查所需技能，查询工具直接执行，写工具只保存待确认记录；确认执行时再次检查权限和读写类型。 */
@Component
@RequiredArgsConstructor
public class ToolDispatcher {
    /** 按名称查找工具定义，未注册的工具直接拒绝。 */
    private final ToolRegistry registry;

    /** 加载技能手册，并检查调用工具所需的技能是否已加载。 */
    private final SkillService skills;

    /** 写操作不直接执行，而是创建待确认操作；并检查会话里是否已有未处理的确认卡。 */
    private final PendingActionService pendingActions;

    /** 用户确认后执行下单。 */
    private final OrderService orders;

    /** 先检查技能；查询工具立即执行，写工具仅创建或返回待确认操作。 */
    public Object dispatch(
            String toolName, ToolExecutionContext context, Map<String, Object> arguments) {
        return dispatch(toolName, context, arguments, null);
    }

    /** 通过技能校验后通知诊断计数。写工具的这一步只分发提议，不执行确认后的交易。 */
    Object dispatch(String toolName, ToolExecutionContext context, Map<String, Object> arguments,
            Runnable onDispatch) {
        RegisteredTool tool = registry.require(toolName);
        checkSkill(tool, context.getConversationId());
        if (onDispatch != null) {
            onDispatch.run();
        }
        // 写工具第一次被模型调用时，只生成确认卡，不直接调用 handler。
        if (tool.getRisk() == RegisteredTool.Risk.WRITE) {
            PendingActionDO existing = pendingActions.latest(context.getConversationId());
            if (existing != null) {
                return Map.of(
                        "status",
                        "AWAITING_CONFIRMATION",
                        "pendingActionId",
                        existing.getId(),
                        "summary",
                        existing.getSummary(),
                        "message",
                        "已有操作待确认，不能创建另一笔操作");
            }
            PendingActionDO pending = pendingActions.create(context, tool, arguments);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "AWAITING_CONFIRMATION");
            result.put("pendingActionId", pending.getId());
            result.put("summary", pending.getSummary());
            result.put("message", "操作尚未执行，正在等待用户确认");
            return result;
        }
        // 走到这里的是查询工具，可以直接返回业务结果；写处理器从 executeConfirmed 进入。
        return tool.getHandler().execute(context, arguments);
    }

    /** 用户批准后恢复持久化参数，再检查技能及写类型，调用真正业务处理器。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeConfirmed(PendingActionDO action) {
        RegisteredTool tool = registry.require(action.getToolName());
        checkSkill(tool, action.getConversationId());
        if (tool.getRisk() != RegisteredTool.Risk.WRITE) {
            throw new IllegalStateException("待确认记录不是写操作");
        }
        if ("order_create".equals(action.getToolName())) {
            return orders.submitAction(action.getUserId(), action.getId());
        }
        Object result =
                tool.getHandler()
                        .execute(
                                new ToolExecutionContext(
                                        action.getUserId(),
                                        action.getConversationId(),
                                        action.getId()),
                                action.getArguments());
        return result instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of("result", result);
    }

    /** 所需技能必须既存在又已在当前会话加载；工具名即使被猜中也不能跳过检查。 */
    private void checkSkill(RegisteredTool tool, String conversationId) {
        if (tool.getRequiredSkill() != null
                && (!skills.loadedSkillCodes(conversationId).contains(tool.getRequiredSkill())
                        || skills.find(tool.getRequiredSkill()).isEmpty())) {
            throw new IllegalStateException("工具需要先加载技能 " + tool.getRequiredSkill());
        }
    }
}
