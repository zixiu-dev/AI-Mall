package com.bitmall.agent.agent.service.tool.assistant;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.READ;
import static com.bitmall.agent.agent.service.tool.support.ToolArguments.*;
import static com.bitmall.agent.agent.service.tool.support.ToolDefinitions.*;

import com.bitmall.agent.agent.dao.entity.SkillDO;
import com.bitmall.agent.agent.service.memory.MemoryService;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolProvider;
import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 提供知识检索、当前日期、加载技能和保存偏好四个辅助工具。 加载技能和保存偏好会写助手状态，但不会直接改变商城交易。 */
@Component
@RequiredArgsConstructor
public class AssistantTools implements ToolProvider {
  /** 检索售后政策等知识。 */
  private final KnowledgeService knowledge;

  /** 加载业务办理手册（技能）到当前会话。 */
  private final SkillService skills;

  /** 保存用户明确要求记住的偏好。 */
  private final MemoryService memories;

  /** 声明本功能的工具定义，启动时由 ToolRegistry 收集并检查名称。 */
  @Override
  public List<RegisteredTool> tools() {
    return List.of(
        tool(
            "search_knowledge",
            "按品牌和文档类别检索有出处的政策。query 保留完整用户原问题；可用 queries 提供 1～3 个知识子问题，但不能同时传"
                + " brand/category/scope。单问题可用这些筛选。本站购买的商品用 PLATFORM"
                + " 范围，不能把官方渠道政策套用本站。结果可能需要补问或无资料。",
            """
{"type":"object","properties":{"query":{"type":"string","minLength":1,"maxLength":1000},"queries":{"type":"array","minItems":1,"maxItems":20,"description":"去重后1～3个知识子问题；不包含下单或查订单任务，与brand/category/scope互斥","items":{"type":"string","minLength":1,"maxLength":500}},"brand":{"type":"string","enum":["APPLE","XIAOMI","NIKE","IKEA","MUJI","PLATFORM"]},"category":{"type":"string","enum":["RETURNS","REFUND","WARRANTY","SHIPPING","ORDER","COUPON"]},"scope":{"type":"string","enum":["AUTO","BRAND","PLATFORM"]}},"required":["query"],"additionalProperties":false}
""",
            null,
            READ,
            (ctx, a) ->
                knowledge.retrieve(KnowledgeRetrievalRequest.from(a), ctx.getKnowledgeContext())),
        tool(
            "current_date",
            "获取当前日期，用于理解今天、昨天、明天等相对时间。",
            NO_ARGS,
            null,
            READ,
            (ctx, a) -> Map.of("date", LocalDate.now(ZoneId.of("Asia/Shanghai")).toString())),
        tool(
            "load_skill",
            "加载办事技能手册。可用技能：order_create_flow（下单）、order_cancel_flow（取消订单）、coupon_ops（领取优惠券）、after_sales（退款/退货/换货）。",
            requiredSchema("skillCode", "string"),
            null,
            READ,
            (ctx, a) -> {
              SkillDO skill = skills.load(ctx.getConversationId(), text(a, "skillCode", null));
              return Map.of(
                  "loaded",
                  true,
                  "skillCode",
                  skill.getCode(),
                  "name",
                  skill.getName(),
                  "manual",
                  skill.getManual(),
                  "unlockedTools",
                  skill.getToolNames());
            }),
        tool(
            "save_preference",
            "仅当用户明确要求记住或更新稳定购物偏好时使用。不要保存订单、物流等易变化业务事实。",
            requiredSchema(new String[] {"key", "value"}, "key", "string", "value", "string"),
            null,
            READ,
            (ctx, a) -> {
              memories.upsert(
                  ctx.getUserId(),
                  text(a, "key", null),
                  text(a, "value", null),
                  ctx.getConversationId());
              return Map.of("saved", true, "key", a.get("key"), "value", a.get("value"));
            }));
  }
}
