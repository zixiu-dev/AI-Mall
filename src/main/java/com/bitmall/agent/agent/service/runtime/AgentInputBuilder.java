package com.bitmall.agent.agent.service.runtime;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 组装本轮发给模型的消息列表：已保存的模型上下文 + 必要的补充 + 本轮用户消息。
 *
 * <p>补充分三种情况：
 *
 * <ul>
 *   <li>上一轮中断、留下了没有结果的工具调用：补一条"未完成"结果，否则模型 API 会因调用和结果不配对而报错；
 *   <li>这是会话第一次走新的模型上下文（旧版本只保存了页面消息）：把页面上的历史消息导入进来；
 *   <li>用户在页面上点了确认/取消：后端执行回执不在模型上下文里，作为助手消息补进来，让模型知道实际结果。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AgentInputBuilder {
  /** 消息元数据里的页面消息编号，用来判断某条页面消息是否已经在模型上下文里。 */
  private static final String CHAT_ID = "chatId";

  /** 补给中断工具调用的结果内容。 */
  private static final String INTERRUPTED_RESULT =
      "{\"ok\":false,\"error\":\"上轮工具调用未完成结果记录，未自动重放；请以商城业务查询和确认回执为准。\"}";

  /** 读取本会话保存的模型上下文（含工具调用和结果）。 */
  private final AgentStateStoreService store;

  public List<Message> build(
      long user, String session, String message, List<ChatMessageDO> recent) {
    List<Message> history = store.load(Long.toString(user), session);
    closeInterruptedCalls(history);
    boolean firstTurnWithModelState =
        history.stream().noneMatch(m -> m.getMessageType() == MessageType.USER);
    if (firstTurnWithModelState) {
      importPageHistory(history, recent);
    } else {
      appendActionReceipts(history, recent);
    }
    history.add(new UserMessage(message));
    return history;
  }

  /**
   * 把页面上的普通历史消息导入模型上下文。
   *
   * <p>只导入用户和助手的文字消息，旧版本的工具调用无法可靠转换，跳过。 recent 的最后一条是本轮用户消息，由 build 统一追加，这里不导入。
   */
  private static void importPageHistory(List<Message> history, List<ChatMessageDO> recent) {
    for (int i = 0; i < Math.max(0, recent.size() - 1); i++) {
      ChatMessageDO item = recent.get(i);
      if ("tool".equals(item.getRole())) {
        continue;
      }
      Map<String, Object> metadata = Map.of(CHAT_ID, Long.toString(item.getId()));
      if ("assistant".equals(item.getRole())) {
        history.add(
            AssistantMessage.builder().content(item.getContent()).properties(metadata).build());
      } else {
        history.add(UserMessage.builder().text(item.getContent()).metadata(metadata).build());
      }
    }
  }

  /** 把模型上下文里还没有的确认/取消回执（元数据带 decision 的页面消息）补进去。 */
  private static void appendActionReceipts(List<Message> history, List<ChatMessageDO> recent) {
    Set<Object> chatIdsInHistory = new HashSet<>();
    history.forEach(m -> chatIdsInHistory.add(m.getMetadata().get(CHAT_ID)));
    for (ChatMessageDO item : recent) {
      boolean isReceipt = item.getMetadata().containsKey("decision");
      boolean alreadyInHistory = chatIdsInHistory.contains(Long.toString(item.getId()));
      if (isReceipt && !alreadyInHistory) {
        history.add(
            AssistantMessage.builder()
                .content("商城后端操作回执：" + item.getContent())
                .properties(Map.of(CHAT_ID, Long.toString(item.getId())))
                .build());
      }
    }
  }

  /**
   * 给没有结果的工具调用补一条"未完成"结果，插在原调用紧后面。
   *
   * <p>只补结果，不重新执行工具（可能是写操作）；插在调用后面，保证下一轮的用户消息不会夹在调用和结果之间。
   */
  void closeInterruptedCalls(List<Message> history) {
    for (int i = 0; i < history.size(); i++) {
      if (!(history.get(i) instanceof AssistantMessage assistant) || !assistant.hasToolCalls()) {
        continue;
      }
      // 收集紧跟在这条调用后面的所有工具结果。
      Set<String> answeredCallIds = new HashSet<>();
      int afterResponses = i + 1;
      while (afterResponses < history.size()
          && history.get(afterResponses) instanceof ToolResponseMessage responses) {
        responses.getResponses().forEach(response -> answeredCallIds.add(response.id()));
        afterResponses++;
      }
      List<ToolResponseMessage.ToolResponse> missing =
          assistant.getToolCalls().stream()
              .filter(call -> !answeredCallIds.contains(call.id()))
              .map(
                  call ->
                      new ToolResponseMessage.ToolResponse(
                          call.id(), call.name(), INTERRUPTED_RESULT))
              .toList();
      if (!missing.isEmpty()) {
        history.add(
            afterResponses,
            ToolResponseMessage.builder()
                .responses(missing)
                .metadata(Map.of("interrupted", true))
                .build());
      }
    }
  }
}
