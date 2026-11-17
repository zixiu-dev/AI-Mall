package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeEvidencePacker;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 项目工具与 Spring AI 之间的适配器。 检查可信上下文、本轮开放名单和参数类型，再分发业务；同时包装错误、技能标记和超长结果。 */
public final class CommerceToolCallback implements ToolCallback {
  /** 加载手册成功时写入工具结果的元数据键，中间件据此识别仍可见的技能。 */
  public static final String LOADED_SKILL = "wowo_loaded_skill";

  /** 当前适配器对应的工具定义，包含名称、Schema、技能要求和处理函数。 */
  private final RegisteredTool tool;

  /** 依赖组件：工具调用的统一分发入口。 */
  private final ToolDispatcher dispatcher;

  /** 依赖组件：业务技能目录和会话加载状态的入口。 */
  private final SkillService skills;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper json;

  /** 普通工具结果的字符预算，超出时返回带标记的预览。 */
  private final int maxResultChars;

  /** 解析后的工具参数 Schema，发给模型说明字段格式，不是本轮真实参数。 */
  private final Map<String, Object> parameters;

  /** 同一份 Schema 的树形表示，用于执行前检查必填字段、未知字段和类型。 */
  private final JsonNode schema;

  /** 由 DynamicToolCatalog 为每个工具手动创建适配器，保存分发依赖并解析参数 Schema；格式错误会在运行工具前被发现。 */
  public CommerceToolCallback(
      RegisteredTool tool,
      ToolDispatcher dispatcher,
      SkillService skills,
      ObjectMapper json,
      AgentProperties properties) {
    this.tool = tool;
    this.dispatcher = dispatcher;
    this.skills = skills;
    this.json = json;
    this.maxResultChars = properties.getMaxToolResultChars();
    try {
      parameters = json.readValue(tool.getInputSchema(), new TypeReference<>() {});
      schema = json.readTree(tool.getInputSchema());
    } catch (Exception e) {
      throw new IllegalArgumentException("无效工具 Schema: " + tool.getName(), e);
    }
  }

  /** 返回模型调用此工具时必须使用的名称。 */
  public String getName() {
    return tool.getName();
  }

  /** 普通工具返回静态说明；load_skill 根据数据库目录动态生成可加载技能介绍。 */
  public String getDescription() {
    if (!"load_skill".equals(getName())) return tool.getDescription();
    return "按需加载业务办理手册。用户明确要求购买或创建订单时，优先加载 order_create_flow；明确要求取消订单时，优先加载 order_cancel_flow"
        + " 后再推进办理；领取优惠券前加载 coupon_ops。普通浏览、比较或查询不必加载。可用技能：\n"
        + skills.listEnabled().stream()
            .map(s -> "- " + s.getCode() + "（" + s.getName() + "）：" + s.getDescription())
            .collect(Collectors.joining("\n"));
  }

  /** 返回已经解析的参数 Schema，让模型知道允许的字段及类型。 */
  public Map<String, Object> getParameters() {
    return parameters;
  }

  /** 声明此工具是否完全只读；加载技能和保存偏好虽然不交易，也会改变助手状态。 */
  public boolean isReadOnly() {
    return tool.getRisk() == RegisteredTool.Risk.READ
        && !Set.of("load_skill", "save_preference").contains(getName());
  }

  /** Spring AI 使用此定义向模型声明工具；实际可见集合仍由每轮技能策略筛选。 */
  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(getName())
        .description(getDescription())
        .inputSchema(tool.getInputSchema())
        .build();
  }

  /** 缺少后端 ToolContext 时一律拒绝，不能直接用 JSON 中的用户字段。 */
  @Override
  public String call(String input) {
    return error("缺少可信业务上下文");
  }

  /** 统一校验入口；通过 Spring AI ToolContext 传递身份及本轮允许集合，不发送到模型。 */
  @Override
  public String call(String input, ToolContext context) {
    try {
      Object trusted = context.getContext().get(ToolExecutionContext.class.getName());
      if (!(trusted instanceof ToolExecutionContext ctx))
        throw new IllegalStateException("缺少可信业务上下文");
      Object permissions = context.getContext().get(SkillContextPolicy.ALLOWED_TOOLS);
      if (!(permissions instanceof Set<?> allowed) || !allowed.contains(getName()))
        throw new IllegalStateException("工具尚未开放，请先加载对应技能手册");
      Map<String, Object> args = json.readValue(input, new TypeReference<>() {});
      validate(args);
      Object data;
      synchronized (ctx) {
        data = dispatcher.dispatch(getName(), ctx, args);
      }
      if ("product_search".equals(getName())) data = compactSearch(data);
      if ("search_knowledge".equals(getName())) data = compactKnowledge(data);
      String output = json.writeValueAsString(Map.of("ok", true, "data", data));
      if (!"load_skill".equals(getName())
          && !"search_knowledge".equals(getName())
          && tool.getRisk() != RegisteredTool.Risk.WRITE) output = trim(output);
      return output;
    } catch (Exception e) {
      return error(e.getMessage() == null ? "工具执行失败" : e.getMessage());
    }
  }

  private String error(String message) {
    try {
      return json.writeValueAsString(Map.of("ok", false, "error", message));
    } catch (Exception ignored) {
      return "{\"ok\":false,\"error\":\"工具执行失败\"}";
    }
  }

  /** 拒绝缺失必填项、未声明字段和类型错误；详细业务条件仍由 Service 校验。 */
  private void validate(Map<String, Object> args) {
    for (JsonNode required : schema.path("required"))
      if (!args.containsKey(required.asText()) || args.get(required.asText()) == null)
        throw new IllegalArgumentException("缺少参数 " + required.asText());
    args.forEach(
        (name, value) -> {
          JsonNode field = schema.path("properties").get(name);
          if (field == null) throw new IllegalArgumentException("不允许的参数 " + name);
          validateField(name, value, field);
        });
  }

  /** 数组逐个校验，不能只向模型声明 items 却在执行时接受数字或空子问题。 */
  private void validateField(String name, Object value, JsonNode field) {
    boolean valid =
        switch (field.path("type").asText()) {
          case "string" -> value instanceof String;
          case "integer" ->
              value instanceof Number n
                  && new java.math.BigDecimal(n.toString()).stripTrailingZeros().scale() <= 0;
          case "number" -> value instanceof Number;
          case "boolean" -> value instanceof Boolean;
          case "array" -> value instanceof List<?>;
          default -> false;
        };
    if (!valid) throw new IllegalArgumentException("参数类型错误：" + name);
    if (field.has("enum")
        && java.util.stream.StreamSupport.stream(field.path("enum").spliterator(), false)
            .noneMatch(n -> n.equals(json.valueToTree(value))))
      throw new IllegalArgumentException("参数取值不合法：" + name);
    if (value instanceof String text
        && ((field.has("minLength") && text.length() < field.path("minLength").asInt())
            || (field.has("maxLength") && text.length() > field.path("maxLength").asInt())))
      throw new IllegalArgumentException("参数长度错误：" + name);
    if (value instanceof List<?> list) {
      if ((field.has("minItems") && list.size() < field.path("minItems").asInt())
          || (field.has("maxItems") && list.size() > field.path("maxItems").asInt()))
        throw new IllegalArgumentException("数组长度错误：" + name);
      for (int i = 0; i < list.size(); i++)
        validateField(name + "[" + i + "]", list.get(i), field.path("items"));
    }
  }

  /** 按完整商品条目裁剪超长搜索结果，保留可解析 JSON 和继续查询提示。 */
  private Object compactSearch(Object data) throws Exception {
    Map<String, Object> page = json.convertValue(data, new TypeReference<Map<String, Object>>() {});
    List<?> items = (List<?>) page.get("items");
    int returned = items.size();
    while (returned > 1
        && json.writeValueAsString(Map.of("ok", true, "data", page)).length() > maxResultChars) {
      returned--;
      page.put("items", items.subList(0, returned));
      page.put("truncated", true);
      page.put("message", "本页展示数量受上下文预算限制；请缩小条件，或用 limit=1 按 page 浏览，勿跳过本页未展示项");
    }
    return page;
  }

  /** 政策按完整文档裁剪，保留出处与渠道，不把半截正文伪装成完整政策。 */
  private Object compactKnowledge(Object data) throws Exception {
    return KnowledgeEvidencePacker.pack((KnowledgeSearchResultVO) data, json, maxResultChars);
  }

  /** 把超长普通工具结果变成带裁剪标记的预览，技能手册和待确认写结果不走这里。 */
  private String trim(String output) throws Exception {
    if (output.length() <= maxResultChars) return output;
    int size = Math.max(0, maxResultChars / 3);
    return json.writeValueAsString(
        Map.of(
            "ok",
            true,
            "trimmed",
            true,
            "originalChars",
            output.length(),
            "preview",
            output.substring(0, Math.min(size, output.length())),
            "message",
            "结果已裁剪，请缩小查询范围或查询商品详情"));
  }
}
