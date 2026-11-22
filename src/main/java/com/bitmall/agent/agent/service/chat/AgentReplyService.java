package com.bitmall.agent.agent.service.chat;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.agent.service.tool.DynamicToolCatalog;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import com.bitmall.agent.agent.vo.PendingActionVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 组装发给前端的回复和确认文案。 执行成功只能使用后端业务回执生成文案，不能把模型的一句承诺当成订单已经完成。 */
@Service
@RequiredArgsConstructor
public class AgentReplyService {
  /** 读取本会话已加载的技能，随回复返回给前端展示。 */
  private final SkillService skills;

  /** 把本轮获准的项目工具转换为 Spring AI ToolCallback。 */
  private final DynamicToolCatalog toolCatalog;

  /** 读取是否启用真实模型，在调试信息里标注本轮运行模式（spring-ai / demo）。 */
  private final AgentProperties properties;

  /** 把执行结果序列化成 JSON，拼进"操作已经完成：…"的回复（失败时退回 toString）。 */
  private final ObjectMapper objectMapper;

  /** 组装前端回复，包括确认卡、有效技能以及当前可见工具名称。 */
  public AgentReplyVO create(
      String conversationId, long messageId, String content, PendingActionDO pending) {
    List<String> loaded = skills.loadedSkillCodes(conversationId).stream().sorted().toList();
    PendingActionVO view =
        pending == null
            ? null
            : new PendingActionVO(pending.getId(), pending.getToolName(), pending.getSummary());
    Map<String, Object> debug = new LinkedHashMap<>();
    debug.put("mode", properties.isAiEnabled() ? "spring-ai" : "demo");
    debug.put("visibleTools", toolCatalog.visibleToolNames(conversationId));
    return new AgentReplyVO(conversationId, messageId, content, view, loaded, debug);
  }

  /** 按操作类型生成与前端按钮一致的确认提示，明确业务尚未执行。 */
  public String confirmationMessage(PendingActionDO pending) {
    if ("order_cancel".equals(pending.getToolName())) {
      return "取消申请已准备好，订单尚未取消。请核对下方确认卡，点击“确认取消订单”继续，或点击“暂不取消”放弃。";
    }
    if ("coupon_claim".equals(pending.getToolName())) {
      return "领券申请已准备好，优惠券尚未领取。请核对下方确认卡，点击“确认领取”继续，或点击“暂不领取”放弃。";
    }
    return "操作已经准备好，但尚未执行。请核对下方确认卡，点击“确认执行”继续，或点击“取消”放弃。";
  }

  /** 使用真实后端回执生成确定的成功说明，不让模型猜测订单或售后编号。 */
  public String renderSuccess(PendingActionDO action, Map<String, Object> result) {
    return switch (action.getToolName()) {
      case "order_create" ->
          "PROCESSING".equals(result.get("status"))
              ? "下单已受理，正在排队处理，尚未创建订单。查询编号：" + result.get("traceId") + "。可以稍后查询进度。"
              : "订单已经创建成功，订单号："
                  + result.get("orderId")
                  + "，应付 ¥"
                  + result.get("payAmount")
                  + "，当前待支付，请前往订单中心支付。";
      case "order_cancel" ->
          "订单 "
              + result.get("orderId")
              + " 已取消，库存已返还，优惠券占用已解除；券能否使用仍以有效期和使用条件为准。当前为演示交易，不涉及真实支付退款。";
      case "coupon_claim" -> "优惠券领取成功：" + result.get("name") + "。已加入账户，能否用于具体订单及实际优惠金额以结算校验为准。";
      case "after_sales_submit" -> "售后申请已经提交，售后单号：" + result.get("afterSalesId") + "，当前状态：审核中。";
      default -> "操作已经完成：" + writeJson(result);
    };
  }

  /** 把工具执行结果写成会话可保存的 JSON；序列化失败时保留可阅读文字。 */
  public String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }
}
