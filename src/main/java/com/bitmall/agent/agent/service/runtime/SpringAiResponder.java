package com.bitmall.agent.agent.service.runtime;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.gateway.model.ModelGateway;
import com.bitmall.agent.agent.model.TextChunk;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.conversation.AgentHistorySelector;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import com.bitmall.agent.agent.service.tool.DynamicToolCatalog;
import com.bitmall.agent.agent.service.tool.ToolExecutor;
import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 真实模型模式下的 Agent 主循环。
 *
 * <p>每一步：裁剪历史并计算本轮可用工具 -> 调用模型 -> 如果模型要求调用工具就逐个执行并把结果加入历史，然后进入下一步 -> 模型给出纯文本回答时结束。
 *
 * <p>安全边界：模型不能自行确认交易，也不能选择用户身份；身份由后端通过 ToolContext
 * 传给工具。模型与工具回执会持久化；中断后由下一次用户请求读取历史，补齐缺失回执，再开始新一轮执行。
 */
@Component
@RequiredArgsConstructor
public class SpringAiResponder implements AgentResponder {

  /** 模型单次回复最多允许请求的工具调用数，防止一次性发起大量操作。 */
  private static final int MAX_TOOL_CALLS_PER_ANSWER = 16;

  private static final String TOOL_START_EVENT = "tool_start";
  private static final String TOOL_END_EVENT = "tool_end";
  private static final String LOAD_SKILL_TOOL = "load_skill";

  /** 修复一次后模型仍声称有确认卡时，用这段固定回复替换模型回答。 */
  private static final String NO_CONFIRMATION_CARD_REPLY =
      "本次还没有生成可点击的确认卡，本次操作尚未执行。请重新发起办理请求；只有页面实际显示确认卡后才能确认执行。";

  /** 发现模型声称有确认卡但数据库中没有时，追加到系统提示里，让模型重新处理一次。 */
  private static final String MISSING_CONFIRMATION_HINT =
      "\n"
          + "后台校验：当前会话没有待确认记录，刚才指引点击确认按钮的回答不成立。继续处理原始请求，不能把校验当用户授权。只在用户明确要求办理且信息完整时调用写工具生成提议，缺少技能先加载。不得自行确认。工具返回"
          + " pendingActionId 才能提示点击确认；否则明确尚未生成。";

  private static final String STEP_LIMIT_REPLY = "本次任务已达到执行步数上限。请缩小任务范围后继续；已经准备好的操作以页面确认卡为准。";

  /** 执行模型请求的工具并返回原回执。 */
  private final ToolExecutor toolExecutor;

  /** 每次推理通过模型网关执行，切换候选不重放已完成的工具。 */
  private final ModelGateway modelGateway;

  /** 用于安排模型和工具的超时检查。 */
  private final ScheduledExecutorService scheduler;

  /** 组装本轮系统提示词：固定系统说明 + 较早会话摘要 + 用户已保存的偏好。 */
  private final AgentPromptService prompts;

  /** 恢复模型历史，补齐中断时缺失的工具回执，并加入本轮用户输入。 */
  private final AgentInputBuilder inputs;

  /** 按完整用户轮次裁剪历史，保证工具请求和工具结果成对保留。 */
  private final AgentHistorySelector history;

  /** 根据当前可见的技能手册计算本轮允许调用的工具。 */
  private final SkillContextPolicy skills;

  /** 按本轮可用技能提供工具回调列表，交给模型调用。 */
  private final DynamicToolCatalog catalog;

  /** 保存模型消息和工具调用结果，支持中断后在下一轮继续。 */
  private final AgentStateStoreService store;

  /** 查询会话是否真有待确认操作，用来识别模型声称"请点击确认卡"但实际并没有生成确认卡的回答。 */
  private final PendingActionService pending;

  /** 读取单次请求超时和模型推理的最大循环轮数。 */
  private final AgentProperties properties;

  /** 读取加载技能回执，保存主循环需要的技能元数据。 */
  private final ObjectMapper json;

  /** 非流式调用：自己创建执行上下文（含整体超时），丢弃中间事件，只返回最终回答。 */
  @Override
  public String respond(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages) {
    try (AgentExecution execution =
        new AgentExecution(scheduler, properties.getRequestTimeoutSeconds())) {
      execution.bind();
      return respondStream(
          userId, conversationId, requestId, message, recentMessages, execution, chunk -> {});
    }
  }

  /**
   * 运行 Agent 主循环，文本增量和工具进度通过 events 实时推送。
   *
   * <p>最多执行 maxIterations
   * 步。如果模型回答里引导用户"点击确认"，但数据库里并没有待确认操作，会追加系统提示让模型重试一次；重试后仍如此，则用固定话术替换，避免用户以为操作已经准备好。
   */
  public String respondStream(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages,
      AgentExecution execution,
      Consumer<TextChunk> events) {
    AgentRunState run = startRun(userId, conversationId, requestId, message, recentMessages);

    for (int iteration = 0; iteration < properties.getMaxIterations(); iteration++) {
      execution.check();

      // 1. 先裁剪历史，再根据仍可见的技能手册开放工具，保存实际会发给模型的历史。
      List<Message> modelInput = trimHistory(run);
      Set<String> allowedTools = skills.allowed(conversationId, modelInput);
      List<ToolCallback> callbacks = catalog.callbacks(allowedTools);
      saveHistory(run);

      // 2. 等待模型完整回复，实时转发文本；工具参数分片由 streamReader 合并。
      int stepNo = iteration + 1;
      AssistantMessage answer = callModel(modelInput, callbacks, execution, events, stepNo);
      run.messages.add(answer);
      saveHistory(run);

      // 3. 工具逐个执行并保存回执；下一步推理才能看到执行结果。
      if (answer.hasToolCalls()) {
        executeToolCalls(run, answer, callbacks, allowedTools, execution, events, stepNo);
        continue;
      }

      // 4. 纯文本回答结束本轮；声称已生成确认卡时，还要核对后端记录。
      if (!claimsConfirmationCardWithoutPendingAction(conversationId, answer.getText())) {
        return answer.getText();
      }
      if (run.confirmationRepairTried) {
        return replaceLastAnswer(run, NO_CONFIRMATION_CARD_REPLY, events);
      }
      run.confirmationRepairTried = true;
      run.systemPrompt += MISSING_CONFIRMATION_HINT;
      events.accept(TextChunk.reset());
    }

    execution.check();
    return appendFinalAnswer(run, STEP_LIMIT_REPLY, events);
  }

  /** 创建本次请求的运行状态：工具执行上下文、系统提示词和恢复后的模型历史。 */
  private AgentRunState startRun(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages) {
    List<String> recentLines =
        recentMessages.stream()
            .map(messageLine -> messageLine.getRole() + ":" + messageLine.getContent())
            .toList();
    KnowledgeExecutionContext knowledgeContext =
        new KnowledgeExecutionContext(
            requestId, message, recentLines, properties.getRequestTimeoutSeconds() * 1000L);
    ToolExecutionContext toolContext =
        new ToolExecutionContext(userId, conversationId, requestId, knowledgeContext);
    return new AgentRunState(
        toolContext,
        prompts.build(userId, conversationId),
        inputs.build(userId, conversationId, message, recentMessages));
  }

  /**
   * 把系统提示和历史一起交给裁剪器，返回本步发给模型的完整消息列表（含系统消息）。
   *
   * <p>裁剪后的非系统消息写回 run.messages，后续保存的就是裁剪后的历史；系统提示单独保存在 run.systemPrompt 中。
   */
  private List<Message> trimHistory(AgentRunState run) {
    List<Message> untrimmed = new ArrayList<>();
    untrimmed.add(new SystemMessage(run.systemPrompt));
    untrimmed.addAll(run.messages);
    List<Message> trimmed = history.selectHistory(untrimmed);
    run.messages =
        new ArrayList<>(
            trimmed.stream()
                .filter(modelMessage -> modelMessage.getMessageType() != MessageType.SYSTEM)
                .toList());
    return trimmed;
  }

  /** 在模型阶段超时预算内调用模型，文本增量实时转发给页面。 */
  private AssistantMessage callModel(
      List<Message> modelInput,
      List<ToolCallback> callbacks,
      AgentExecution execution,
      Consumer<TextChunk> events,
      int stepNo) {
    return modelGateway.generate(modelInput, callbacks, execution,
        delta -> events.accept(TextChunk.delta(delta)), () -> events.accept(TextChunk.reset()), stepNo)
        .getResult().getOutput();
  }

  /** 先通知页面清空已经输出的临时文本（模型转去调用工具了），再按顺序执行每个工具。 */
  private void executeToolCalls(
      AgentRunState run,
      AssistantMessage answer,
      List<ToolCallback> callbacks,
      Set<String> allowedTools,
      AgentExecution execution,
      Consumer<TextChunk> events,
      int stepNo) {
    if (answer.getToolCalls().size() > MAX_TOOL_CALLS_PER_ANSWER) {
      throw new IllegalStateException("单次工具数量超过预算");
    }
    events.accept(TextChunk.reset());
    for (AssistantMessage.ToolCall call : answer.getToolCalls()) {
      execution.check();
      executeToolCall(run, call, callbacks, allowedTools, execution, events, stepNo);
    }
  }

  /** 调用工具执行器，把返回的回执加入历史并通知页面。 */
  private void executeToolCall(
      AgentRunState run,
      AssistantMessage.ToolCall call,
      List<ToolCallback> callbacks,
      Set<String> allowedTools,
      AgentExecution execution,
      Consumer<TextChunk> events,
      int stepNo) {
    events.accept(new TextChunk(TOOL_START_EVENT, call.name()));
    String result = toolExecutor.execute(call, callbacks, allowedTools, run.toolContext, execution, stepNo);
    execution.check();

    run.messages.add(
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result)))
            .metadata(toolResponseMetadata(call, result))
            .build());
    saveHistory(run);
    events.accept(new TextChunk(TOOL_END_EVENT, call.name()));
  }

  /** load_skill 成功时在回执元数据里记录"哪次调用加载了哪个技能"，技能策略据此判断后续可开放的工具。 */
  private Map<String, Object> toolResponseMetadata(AssistantMessage.ToolCall call, String result) {
    Map<String, Object> metadata = new HashMap<>();
    boolean skillLoaded =
        LOAD_SKILL_TOOL.equals(call.name()) && json.readTree(result).path("ok").asBoolean();
    if (skillLoaded) {
      String skillCode = json.readTree(call.arguments()).path("skillCode").asText();
      metadata.put(SkillContextPolicy.LOADED_SKILLS, Map.of(call.id(), skillCode));
    }
    return metadata;
  }

  /** 用固定话术替换最后一条模型回答，并通知页面清空已显示文本、改为显示新话术。 */
  private String replaceLastAnswer(AgentRunState run, String reply, Consumer<TextChunk> events) {
    run.messages.set(run.messages.size() - 1, new AssistantMessage(reply));
    saveHistory(run);
    events.accept(TextChunk.reset());
    events.accept(TextChunk.delta(reply));
    return reply;
  }

  /** 追加一条固定话术作为最终回答，并通知页面清空已显示文本、改为显示新话术。 */
  private String appendFinalAnswer(AgentRunState run, String reply, Consumer<TextChunk> events) {
    run.messages.add(new AssistantMessage(reply));
    saveHistory(run);
    events.accept(TextChunk.reset());
    events.accept(TextChunk.delta(reply));
    return reply;
  }

  /** 保存本轮用户所属会话的模型历史，后续请求据此恢复上下文。 */
  private void saveHistory(AgentRunState run) {
    store.save(
        Long.toString(run.toolContext.getUserId()),
        run.toolContext.getConversationId(),
        run.messages);
  }

  /**
   * 判断模型回答是否在引导用户点击确认卡，而数据库中其实没有待确认操作。
   *
   * <p>识别方式：去掉 Markdown 标记后，同时出现"点击/请核对"和"确认执行/确认卡"等字样，且没有"尚未生成"这类否定说明。
   */
  private boolean claimsConfirmationCardWithoutPendingAction(String conversationId, String text) {
    if (text == null) {
      return false;
    }
    if (pending.latest(conversationId) != null) {
      return false;
    }
    String plain = text.replace("**", "").replace("`", "");
    boolean asksUserToAct = plain.contains("点击") || plain.contains("请核对");
    boolean mentionsConfirmation =
        plain.contains("确认执行")
            || plain.contains("确认取消订单")
            || plain.contains("下方确认卡")
            || plain.contains("下方的确认卡");
    boolean admitsNotCreated =
        plain.contains("尚未生成") || plain.contains("没有生成") || plain.contains("无法生成");
    return asksUserToAct && mentionsConfirmation && !admitsNotCreated;
  }

  /** 单次请求的运行状态。每次请求新建，不放在单例字段里，避免不同用户之间共享身份或消息。 */
  private static final class AgentRunState {
    /** 后端确认的用户身份和请求信息，工具调用时传给工具。 */
    final ToolExecutionContext toolContext;

    /** 系统提示词；确认卡校验失败时会追加提示。 */
    String systemPrompt;

    /** 不含系统消息的模型历史，每次变化后都会持久化。 */
    List<Message> messages;

    /** 是否已经因"声称有确认卡但实际没有"让模型重试过一次。 */
    boolean confirmationRepairTried;

    AgentRunState(ToolExecutionContext toolContext, String systemPrompt, List<Message> messages) {
      this.toolContext = toolContext;
      this.systemPrompt = systemPrompt;
      this.messages = messages;
    }
  }
}
