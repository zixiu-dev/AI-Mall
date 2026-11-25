package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.SkillDO;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.knowledge.service.retrieval.KnowledgeEvidencePacker;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.math.BigDecimal;
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

/**
 * 把项目内的一个业务工具包装成 Spring AI 可调用的 ToolCallback。
 *
 * <p>每次模型调用工具时，按顺序做这些事：
 *
 * <ol>
 *   <li>从后端 ToolContext 取出可信的用户身份，不信任模型传来的 JSON 里的用户字段；
 *   <li>确认这个工具在本轮已经开放（对应技能手册已加载）；
 *   <li>按工具 Schema 校验参数的必填项、字段名和类型；
 *   <li>交给 ToolDispatcher 执行业务；
 *   <li>把结果包装成 {"ok":true,"data":...}，过长时裁剪，失败时包装成 {"ok":false,"error":...}。
 * </ol>
 */
@lombok.extern.slf4j.Slf4j
public final class CommerceToolCallback implements ToolCallback {
  /** 加载手册成功时写入工具结果的元数据键，中间件据此识别仍可见的技能。 */
  public static final String LOADED_SKILL = "wowo_loaded_skill";
  /** 后端 ToolContext 可选的执行观察器，在参数和技能校验通过后通知调用方。 */
  static final String DISPATCH_OBSERVER_KEY = "agent.trace.onDispatch";

  private static final String LOAD_SKILL_TOOL = "load_skill";
  private static final String SAVE_PREFERENCE_TOOL = "save_preference";
  private static final String PRODUCT_SEARCH_TOOL = "product_search";
  private static final String SEARCH_KNOWLEDGE_TOOL = "search_knowledge";

  /** 加载技能、保存偏好虽然是 READ 风险，但会改变助手状态，因此不算"完全只读"。 */
  private static final Set<String> STATEFUL_READ_TOOLS = Set.of(LOAD_SKILL_TOOL, SAVE_PREFERENCE_TOOL);

  private static final String MISSING_TRUSTED_CONTEXT = "缺少可信业务上下文";
  private static final String DEFAULT_ERROR_MESSAGE = "工具执行失败";

  /** 连 error JSON 都序列化失败时的兜底输出，内容与 DEFAULT_ERROR_MESSAGE 一致。 */
  private static final String FALLBACK_ERROR_JSON = "{\"ok\":false,\"error\":\"工具执行失败\"}";

  /** 当前适配器对应的工具定义，包含名称、Schema、技能要求和风险等级。 */
  private final RegisteredTool tool;

  /** 按工具名把调用分发到对应的业务服务（商品、订单、优惠券等）。 */
  private final ToolDispatcher dispatcher;

  /** 读取当前启用的技能列表，动态生成 load_skill 工具的说明。 */
  private final SkillService skills;

  /** 解析工具 Schema 和模型传入的参数，序列化工具返回结果。 */
  private final ObjectMapper json;

  /** 普通工具结果的字符预算，超出时返回带标记的预览。 */
  private final int maxResultChars;

  /** 解析后的参数 Schema，发给模型说明字段格式（不是本轮真实参数）。 */
  private final Map<String, Object> parameters;

  /** 同一份 Schema 的树形表示，执行前用它检查必填字段、未知字段和类型。 */
  private final JsonNode schema;

  /** 由 DynamicToolCatalog 为每个工具创建；构造时就解析 Schema，格式错误会在运行工具前暴露。 */
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

  /** 普通工具返回静态说明；load_skill 的说明里需要列出数据库中当前启用的技能，所以每次动态生成。 */
  public String getDescription() {
    if (!LOAD_SKILL_TOOL.equals(getName())) {
      return tool.getDescription();
    }
    String enabledSkills =
        skills.listEnabled().stream()
            .map(CommerceToolCallback::describeSkill)
            .collect(Collectors.joining("\n"));
    return "按需加载业务办理手册。用户明确要求购买或创建订单时，优先加载 order_create_flow；明确要求取消订单时，优先加载 order_cancel_flow"
        + " 后再推进办理；领取优惠券前加载 coupon_ops。普通浏览、比较或查询不必加载。可用技能：\n"
        + enabledSkills;
  }

  /** 返回已经解析的参数 Schema，让模型知道允许的字段及类型。 */
  public Map<String, Object> getParameters() {
    return parameters;
  }

  /** 是否完全只读：风险等级为 READ，且不是加载技能、保存偏好这类会改变助手状态的工具。 */
  public boolean isReadOnly() {
    boolean readRisk = tool.getRisk() == RegisteredTool.Risk.READ;
    return readRisk && !STATEFUL_READ_TOOLS.contains(getName());
  }

  /** Spring AI 使用此定义向模型声明工具；每轮实际可见哪些工具仍由技能策略筛选。 */
  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(getName())
        .description(getDescription())
        .inputSchema(tool.getInputSchema())
        .build();
  }

  /** 没有后端 ToolContext 就无法确认用户身份，一律拒绝，不能退而使用 JSON 中的用户字段。 */
  @Override
  public String call(String input) {
    return error(MISSING_TRUSTED_CONTEXT);
  }

  /**
   * 工具调用的统一入口。用户身份和本轮允许的工具集合通过 ToolContext 由后端传入，不会发给模型。
   *
   * <p>业务失败返回结构化错误，让模型看到原因；取消信号向外传播，立即终止本轮。
   */
  @Override
  public String call(String input, ToolContext context) {
    ToolExecutionContext executionContext;
    try {
      executionContext = requireTrustedContext(context);
      requireToolAllowed(context);
    } catch (Exception e) {
      return ToolFailure.result(json, "FORBIDDEN", "工具未开放或缺少可信身份，请核对权限和技能", "STOP");
    }

    Map<String, Object> args;
    try {
      args = json.readValue(input, new TypeReference<>() {});
      if (args == null) throw new IllegalArgumentException("参数必须是 JSON 对象");
      validate(args);
    } catch (Exception e) {
      String reason = e instanceof IllegalArgumentException ? e.getMessage() : "参数必须符合工具声明的 JSON 格式";
      return ToolFailure.result(json, "INVALID_ARGUMENT", reason, "CORRECT_OR_ASK_USER");
    }

    boolean completed = false;
    try {
      Object data;
      synchronized (executionContext) {
        Object observer = context.getContext().get(DISPATCH_OBSERVER_KEY);
        data = observer instanceof Runnable onDispatch
            ? dispatcher.dispatch(getName(), executionContext, args, onDispatch)
            : dispatcher.dispatch(getName(), executionContext, args);
      }
      completed = true;
      data = compactIfLarge(data);
      String output = json.writeValueAsString(Map.of("ok", true, "data", data));
      return shouldTrimOutput() ? trim(output) : output;
    } catch (java.util.concurrent.CancellationException e) {
      throw e;
    } catch (Exception e) {
      log.warn("工具执行异常 tool={} request={}", getName(), executionContext.getRequestId(), e);
      if (ToolFailure.isTransient(e)) {
        return ToolFailure.result(json, isReadOnly() ? "TRANSIENT_ERROR" : "RESULT_UNKNOWN",
            "工具服务暂时不可用，需要核对执行结果", isReadOnly() ? "RETRY_READ" : "QUERY_ORIGINAL_OPERATION");
      }
      if (!completed && (e instanceof IllegalArgumentException
          || e instanceof com.bitmall.agent.common.exception.BusinessRejectedException
          || e instanceof com.bitmall.agent.order.exception.OrderRejectedException)) {
        return ToolFailure.result(json, "BUSINESS_REJECTED", e.getMessage(), "ASK_USER");
      }
      return ToolFailure.result(json, isReadOnly() ? "EXECUTION_FAILED" : "RESULT_UNKNOWN",
          "工具执行异常，请核对当前业务状态", isReadOnly() ? "STOP" : "QUERY_ORIGINAL_OPERATION");
    }
  }

  /** 从 ToolContext 取出后端放入的执行上下文；取不到说明调用不是来自本项目的 Agent 主循环。 */
  private static ToolExecutionContext requireTrustedContext(ToolContext context) {
    Object trusted = context.getContext().get(ToolExecutionContext.class.getName());
    if (trusted instanceof ToolExecutionContext executionContext) {
      return executionContext;
    }
    throw new IllegalStateException(MISSING_TRUSTED_CONTEXT);
  }

  /** 确认本工具在本轮允许名单中；名单由已加载的技能手册决定。 */
  private void requireToolAllowed(ToolContext context) {
    Object permissions = context.getContext().get(SkillContextPolicy.ALLOWED_TOOLS);
    boolean allowed = permissions instanceof Set<?> allowedTools && allowedTools.contains(getName());
    if (!allowed) {
      throw new IllegalStateException("工具尚未开放，请先加载对应技能手册");
    }
  }

  /** 商品搜索和政策检索的结果按完整条目裁剪（而不是截断 JSON 字符串），其他工具原样返回。 */
  private Object compactIfLarge(Object data) throws Exception {
    if (PRODUCT_SEARCH_TOOL.equals(getName())) {
      return compactSearch(data);
    }
    if (SEARCH_KNOWLEDGE_TOOL.equals(getName())) {
      return compactKnowledge(data);
    }
    return data;
  }

  /**
   * 是否需要对最终输出做字符级裁剪。
   *
   * <p>技能手册必须完整交给模型；政策检索已经按文档裁剪过；写操作结果包含待确认信息，截断会让确认卡失真。这三类不裁剪。
   */
  private boolean shouldTrimOutput() {
    boolean isLoadSkill = LOAD_SKILL_TOOL.equals(getName());
    boolean isKnowledgeSearch = SEARCH_KNOWLEDGE_TOOL.equals(getName());
    boolean isWrite = tool.getRisk() == RegisteredTool.Risk.WRITE;
    return !isLoadSkill && !isKnowledgeSearch && !isWrite;
  }

  /** 生成失败结果 JSON。 */
  private String error(String message) {
    try {
      return json.writeValueAsString(Map.of("ok", false, "error", message));
    } catch (Exception ignored) {
      return FALLBACK_ERROR_JSON;
    }
  }

  /** 拒绝缺失必填项、未声明字段和类型错误；具体业务条件（库存、归属等）仍由 Service 校验。 */
  private void validate(Map<String, Object> args) {
    for (JsonNode required : schema.path("required")) {
      String requiredName = required.asText();
      boolean missing = !args.containsKey(requiredName) || args.get(requiredName) == null;
      if (missing) {
        throw new IllegalArgumentException("缺少参数 " + requiredName);
      }
    }
    for (Map.Entry<String, Object> arg : args.entrySet()) {
      String name = arg.getKey();
      JsonNode field = schema.path("properties").get(name);
      if (field == null) {
        throw new IllegalArgumentException("不允许的参数 " + name);
      }
      validateField(name, arg.getValue(), field);
    }
  }

  /**
   * 按字段 Schema 校验一个参数值：类型、枚举、字符串长度、数组长度，数组还会逐个校验元素。
   *
   * <p>数组元素必须逐个校验，否则 Schema 声明了 items 类型，执行时却会接受数字或空的子问题。
   */
  private void validateField(String name, Object value, JsonNode field) {
    if (!matchesType(value, field.path("type").asText())) {
      throw new IllegalArgumentException("参数类型错误：" + name);
    }
    if (field.has("enum") && !isAllowedEnumValue(value, field.path("enum"))) {
      throw new IllegalArgumentException("参数取值不合法：" + name);
    }
    if (value instanceof String text && !withinLimits(text.length(), field, "minLength", "maxLength")) {
      throw new IllegalArgumentException("参数长度错误：" + name);
    }
    if (value instanceof List<?> list) {
      if (!withinLimits(list.size(), field, "minItems", "maxItems")) {
        throw new IllegalArgumentException("数组长度错误：" + name);
      }
      JsonNode itemSchema = field.path("items");
      for (int i = 0; i < list.size(); i++) {
        validateField(name + "[" + i + "]", list.get(i), itemSchema);
      }
    }
  }

  /** 判断值是否符合 JSON Schema 类型；integer 允许 3.0 这类小数部分为零的数字，未知类型一律不通过。 */
  private static boolean matchesType(Object value, String type) {
    return switch (type) {
      case "string" -> value instanceof String;
      case "integer" -> value instanceof Number number && isWholeNumber(number);
      case "number" -> value instanceof Number;
      case "boolean" -> value instanceof Boolean;
      case "array" -> value instanceof List<?>;
      default -> false;
    };
  }

  private static boolean isWholeNumber(Number number) {
    return new BigDecimal(number.toString()).stripTrailingZeros().scale() <= 0;
  }

  /** 把参数值转成 JSON 节点后与枚举逐个比较，避免 Java 类型不同（如 Integer 与 Long）导致误判。 */
  private boolean isAllowedEnumValue(Object value, JsonNode enumValues) {
    JsonNode actual = json.valueToTree(value);
    for (JsonNode allowed : enumValues) {
      if (allowed.equals(actual)) {
        return true;
      }
    }
    return false;
  }

  /** 检查长度是否在 Schema 声明的上下限之内；未声明的限制不检查。 */
  private static boolean withinLimits(int length, JsonNode field, String minKey, String maxKey) {
    boolean tooShort = field.has(minKey) && length < field.path(minKey).asInt();
    boolean tooLong = field.has(maxKey) && length > field.path(maxKey).asInt();
    return !tooShort && !tooLong;
  }

  /** 按完整商品条目裁剪超长搜索结果：每次去掉末尾一条，直到放得下或只剩一条，并提示模型如何继续浏览。 */
  private Object compactSearch(Object data) throws Exception {
    Map<String, Object> page = json.convertValue(data, new TypeReference<Map<String, Object>>() {});
    List<?> items = (List<?>) page.get("items");
    int returned = items.size();
    while (returned > 1 && wrappedLength(page) > maxResultChars) {
      returned--;
      page.put("items", items.subList(0, returned));
      page.put("truncated", true);
      page.put("message", "本页展示数量受上下文预算限制；请缩小条件，或用 limit=1 按 page 浏览，勿跳过本页未展示项");
    }
    return page;
  }

  /** 计算结果包装成最终输出后的字符数。 */
  private int wrappedLength(Map<String, Object> page) throws Exception {
    return json.writeValueAsString(Map.of("ok", true, "data", page)).length();
  }

  /** 政策按完整文档裁剪，保留出处与渠道，不把半截正文伪装成完整政策。 */
  private Object compactKnowledge(Object data) throws Exception {
    return KnowledgeEvidencePacker.pack((KnowledgeSearchResultVO) data, json, maxResultChars);
  }

  /** 超长的普通工具结果改为返回前三分之一预览，并标明已裁剪和原始长度。 */
  private String trim(String output) throws Exception {
    if (output.length() <= maxResultChars) {
      return output;
    }
    int previewSize = Math.max(0, maxResultChars / 3);
    String preview = output.substring(0, Math.min(previewSize, output.length()));
    return json.writeValueAsString(
        Map.of(
            "ok", true,
            "trimmed", true,
            "originalChars", output.length(),
            "preview", preview,
            "message", "结果已裁剪，请缩小查询范围或查询商品详情"));
  }

  private static String describeSkill(SkillDO skill) {
    return "- " + skill.getCode() + "（" + skill.getName() + "）：" + skill.getDescription();
  }
}
