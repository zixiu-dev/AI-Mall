package com.bitmall.agent.agent.service.runtime;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.model.TextChunk;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.conversation.AgentHistorySelector;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import com.bitmall.agent.agent.service.tool.*;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

/** Spring AI Agent 主循环：推理、检查工具、执行、保存结果、继续推理。模型不能自行确认交易或选择用户身份。 */
@Component
public class SpringAiResponder implements AgentResponder {
  private final ChatClient client;
  private final AgentPromptService prompts;
  private final AgentInputBuilder inputs;
  private final AgentHistorySelector history;
  private final SkillContextPolicy skills;
  private final DynamicToolCatalog catalog;
  private final AgentStateStoreService store;
  private final PendingActionService pending;
  private final AgentProperties properties;
  private final ObjectMapper json;

  public SpringAiResponder(
      ChatClient client,
      AgentPromptService prompts,
      AgentInputBuilder inputs,
      AgentHistorySelector history,
      SkillContextPolicy skills,
      DynamicToolCatalog catalog,
      AgentStateStoreService store,
      PendingActionService pending,
      AgentProperties properties,
      ObjectMapper json) {
    this.client = client;
    this.prompts = prompts;
    this.inputs = inputs;
    this.history = history;
    this.skills = skills;
    this.catalog = catalog;
    this.store = store;
    this.pending = pending;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public String respond(
      long user, String session, String request, String text, List<ChatMessageDO> recent) {
    return respondStream(user, session, request, text, recent)
        .collect(
            StringBuilder::new,
            (out, chunk) -> {
              if ("reset".equals(chunk.getType())) out.setLength(0);
              if ("delta".equals(chunk.getType())) out.append(chunk.getContent());
            })
        .map(StringBuilder::toString)
        .block();
  }

  /** 订阅时创建独立上下文。总截止时间不随 token 或工具进度重置，取消订阅会取消模型和后续步骤。 */
  public Flux<TextChunk> respondStream(
      long user, String session, String request, String text, List<ChatMessageDO> recent) {
    return Flux.defer(
            () -> {
              var ctx =
                  new ToolExecutionContext(
                      user,
                      session,
                      request,
                      new KnowledgeExecutionContext(
                          request,
                          text,
                          recent.stream().map(m -> m.getRole() + ":" + m.getContent()).toList(),
                          properties.getRequestTimeoutSeconds() * 1000L));
              Run run =
                  new Run(
                      ctx, prompts.build(user, session), inputs.build(user, session, text, recent));
              Mono<Long> deadline =
                  Mono.delay(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                      .flatMap(ignored -> Mono.error(new TimeoutException("Agent 总执行时间超限")));
              return step(run).takeUntilOther(deadline);
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  /** 每次调用 ChatClient 只做一次推理；显式关闭自动工具 Advisor，保留逐步授权和落库边界。 */
  private Flux<TextChunk> step(Run run) {
    return Flux.defer(
        () -> {
          if (run.iterations++ >= properties.getMaxIterations()) {
            String limit = "本次任务已达到执行步数上限。请缩小任务范围后继续；已经准备好的操作以页面确认卡为准。";
            run.messages.add(new AssistantMessage(limit));
            save(run);
            return Flux.just(TextChunk.reset(), TextChunk.delta(limit));
          }
          List<Message> visible = new ArrayList<>();
          visible.add(new SystemMessage(run.system));
          visible.addAll(run.messages);
          visible = history.selectHistory(visible);
          run.messages =
              new ArrayList<>(
                  visible.stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).toList());
          Set<String> allowed = skills.allowed(run.ctx.getConversationId(), visible);
          List<ToolCallback> callbacks = catalog.callbacks(allowed);
          save(run);
          AtomicReference<ChatResponse> completed = new AtomicReference<>();
          Flux<ChatResponse> source =
              client
                  .prompt()
                  .messages(visible)
                  .options(ToolCallingChatOptions.builder().toolCallbacks(callbacks))
                  .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                  .stream()
                  .chatResponse();
          // MessageAggregator 合并拆分的工具参数；同时把真实文本增量送给前端。
          Flux<TextChunk> deltas =
              new MessageAggregator()
                  .aggregate(source, completed::set)
                  .takeUntilOther(
                      Mono.delay(Duration.ofSeconds(60))
                          .flatMap(x -> Mono.error(new TimeoutException("模型推理超时"))))
                  .filter(r -> r.getResult() != null && r.getResult().getOutput().getText() != null)
                  .map(r -> TextChunk.delta(r.getResult().getOutput().getText()));
          return deltas.concatWith(
              Flux.defer(
                      () -> {
                        ChatResponse response = completed.get();
                        if (response == null || response.getResult() == null)
                          return Flux.error(new IllegalStateException("模型未返回有效结果"));
                        AssistantMessage answer = response.getResult().getOutput();
                        run.messages.add(answer);
                        save(run);
                        if (answer.hasToolCalls()) {
                          if (answer.getToolCalls().size() > 16)
                            return Flux.error(new IllegalStateException("单次工具数量超过预算"));
                          // 一批工具按顺序执行，第一笔提议创建后后续写工具仍会被分发层阻止。
                          return Flux.concat(
                              Flux.just(TextChunk.reset()),
                              Flux.fromIterable(answer.getToolCalls())
                                  .concatMap(call -> execute(run, call, callbacks, allowed)),
                              Flux.defer(() -> step(run)));
                        }
                        if (!missingConfirmation(run.ctx.getConversationId(), answer.getText()))
                          return Flux.empty();
                        if (run.repaired) {
                          String safe = "本次还没有生成可点击的确认卡，本次操作尚未执行。请重新发起办理请求；只有页面实际显示确认卡后才能确认执行。";
                          run.messages.set(run.messages.size() - 1, new AssistantMessage(safe));
                          save(run);
                          return Flux.just(TextChunk.reset(), TextChunk.delta(safe));
                        }
                        run.repaired = true;
                        run.system +=
                            "\n"
                                + "后台校验：当前会话没有待确认记录，刚才指引点击确认按钮的回答不成立。继续处理原始请求，不能把校验当用户授权。只在用户明确要求办理且信息完整时调用写工具生成提议，缺少技能先加载。不得自行确认。工具返回"
                                + " pendingActionId 才能提示点击确认；否则明确尚未生成。";
                        return Flux.concat(Flux.just(TextChunk.reset()), step(run));
                      })
                  .subscribeOn(Schedulers.boundedElastic()));
        });
  }

  /** 限制工具等待并在落库后发送结束事件；超时不宣称业务回滚、不自动重试。 */
  private Flux<TextChunk> execute(
      Run run, AssistantMessage.ToolCall call, List<ToolCallback> callbacks, Set<String> allowed) {
    return Flux.concat(
        Flux.just(new TextChunk("tool_start", call.name())),
        Mono.fromCallable(
                () -> {
                  ToolCallback callback =
                      callbacks.stream()
                          .filter(c -> c.getToolDefinition().name().equals(call.name()))
                          .findFirst()
                          .orElse(null);
                  if (callback == null) return "{\"ok\":false,\"error\":\"工具尚未开放或不存在，请先加载对应技能手册\"}";
                  return callback.call(
                      call.arguments(),
                      new ToolContext(
                          Map.of(
                              ToolExecutionContext.class.getName(),
                              run.ctx,
                              SkillContextPolicy.ALLOWED_TOOLS,
                              allowed)));
                })
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(15))
            .onErrorReturn("{\"ok\":false,\"error\":\"工具调用失败或超时，未自动重试；请查询实际业务状态\"}")
            .publishOn(Schedulers.boundedElastic())
            .map(
                result -> {
                  Map<String, Object> metadata = new HashMap<>();
                  // 只有真实执行成功才能添加手册标记，模型文本不能伪造这份后端元数据。
                  if ("load_skill".equals(call.name())
                      && json.readTree(result).path("ok").asBoolean()) {
                    String code = json.readTree(call.arguments()).path("skillCode").asText();
                    metadata.put(SkillContextPolicy.LOADED_SKILLS, Map.of(call.id(), code));
                  }
                  run.messages.add(
                      ToolResponseMessage.builder()
                          .responses(
                              List.of(
                                  new ToolResponseMessage.ToolResponse(
                                      call.id(), call.name(), result)))
                          .metadata(metadata)
                          .build());
                  save(run);
                  return new TextChunk("tool_end", call.name());
                }));
  }

  private void save(Run run) {
    store.save(Long.toString(run.ctx.getUserId()), run.ctx.getConversationId(), run.messages);
  }

  private boolean missingConfirmation(String session, String text) {
    if (text == null || pending.latest(session) != null) return false;
    String plain = text.replace("**", "").replace("`", "");
    return (plain.contains("点击") || plain.contains("请核对"))
        && (plain.contains("确认执行")
            || plain.contains("确认取消订单")
            || plain.contains("下方确认卡")
            || plain.contains("下方的确认卡"))
        && !plain.contains("尚未生成")
        && !plain.contains("没有生成")
        && !plain.contains("无法生成");
  }

  /** 每个订阅独有的运行状态，不保存在 singleton 字段，不跨用户共享工具身份或消息。 */
  private static final class Run {
    final ToolExecutionContext ctx;
    String system;
    List<Message> messages;
    int iterations;
    boolean repaired;

    Run(ToolExecutionContext ctx, String system, List<Message> messages) {
      this.ctx = ctx;
      this.system = system;
      this.messages = messages;
    }
  }
}
