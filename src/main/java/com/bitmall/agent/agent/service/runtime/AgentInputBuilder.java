package com.bitmall.agent.agent.service.runtime;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;

/** 恢复 Spring AI 消息、关闭未配对调用、导入旧会话页面历史，并补充真实后端确认回执。 */
@Component
public class AgentInputBuilder {
  private final AgentStateStoreService store;

  public AgentInputBuilder(AgentStateStoreService store) {
    this.store = store;
  }

  public List<Message> build(
      long user, String session, String message, List<ChatMessageDO> recent) {
    List<Message> history = store.load(Long.toString(user), session);
    closeInterruptedCalls(history);
    Set<Object> ids = new HashSet<>();
    history.forEach(m -> ids.add(m.getMetadata().get("chatId")));
    boolean first = history.stream().noneMatch(m -> m.getMessageType() == MessageType.USER);
    if (first) {
      // 旧 SDK 状态不转换工具调用，只有页面可验证的普通消息进入新会话。当前用户消息在最后追加一次。
      for (int i = 0; i < Math.max(0, recent.size() - 1); i++) {
        ChatMessageDO item = recent.get(i);
        if ("tool".equals(item.getRole())) continue;
        Map<String, Object> meta = Map.of("chatId", Long.toString(item.getId()));
        if ("assistant".equals(item.getRole()))
          history.add(
              AssistantMessage.builder().content(item.getContent()).properties(meta).build());
        else history.add(UserMessage.builder().text(item.getContent()).metadata(meta).build());
      }
    } else {
      for (ChatMessageDO item : recent)
        if (item.getMetadata().containsKey("decision")
            && !ids.contains(Long.toString(item.getId())))
          history.add(
              AssistantMessage.builder()
                  .content("商城后端操作回执：" + item.getContent())
                  .properties(Map.of("chatId", Long.toString(item.getId())))
                  .build());
    }
    history.add(new UserMessage(message));
    return history;
  }

  /** 将缺失结果补在原调用后面；不执行工具，也不让下一轮消息夹在调用与结果之间。 */
  void closeInterruptedCalls(List<Message> history) {
    for (int i = 0; i < history.size(); i++) {
      if (!(history.get(i) instanceof AssistantMessage a) || !a.hasToolCalls()) continue;
      Set<String> resolved = new HashSet<>();
      int end = i + 1;
      while (end < history.size() && history.get(end) instanceof ToolResponseMessage t) {
        t.getResponses().forEach(r -> resolved.add(r.id()));
        end++;
      }
      List<ToolResponseMessage.ToolResponse> missing =
          a.getToolCalls().stream()
              .filter(c -> !resolved.contains(c.id()))
              .map(
                  c ->
                      new ToolResponseMessage.ToolResponse(
                          c.id(),
                          c.name(),
                          "{\"ok\":false,\"error\":\"上轮工具调用未完成结果记录，未自动重放；请以商城业务查询和确认回执为准。\"}"))
              .toList();
      if (!missing.isEmpty())
        history.add(
            end,
            ToolResponseMessage.builder()
                .responses(missing)
                .metadata(Map.of("interrupted", true))
                .build());
    }
  }
}
