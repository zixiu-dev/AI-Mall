package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.config.AgentProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

/** 为每次模型推理选择可见历史。 按完整用户轮次裁剪，避免只留下工具结果而丢掉对应调用，同时控制消息表示字符预算。 */
@Component
public class AgentHistorySelector {
  /** 依赖组件：读取 application.yml 中 app.agent 配置。 */
  private final AgentProperties properties;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AgentHistorySelector(AgentProperties properties) {
    this.properties = properties;
  }

  /** 按完整用户轮次保留历史；超过字符预算时继续丢弃旧轮次，当前轮次仍超限则拒绝。 */
  public List<Message> selectHistory(List<Message> all) {
    List<Integer> starts = new ArrayList<>();
    for (int i = 0; i < all.size(); i++)
      if (all.get(i).getMessageType() == MessageType.USER) starts.add(i);
    int chars = all.stream().mapToInt(this::chars).sum();
    if (starts.size() <= properties.getKeepRecentTurns()
        && chars <= properties.getMaxContextChars()) return all;
    int first = Math.max(0, starts.size() - properties.getKeepRecentTurns());
    List<Message> selected = all;
    while (first < starts.size()) {
      int cutoff = starts.get(first);
      selected = new ArrayList<>();
      for (int i = 0; i < all.size(); i++)
        if (i >= cutoff || all.get(i).getMessageType() == MessageType.SYSTEM)
          selected.add(all.get(i));
      int size = selected.stream().mapToInt(this::chars).sum();
      if (size <= properties.getMaxContextChars()) return selected;
      first++;
    }
    throw new IllegalStateException("当前任务上下文过大，请缩小查询范围后重新发起任务");
  }

  /** 按消息的完整文本表示估算字符占用；这不是模型的精确 Token 数。 */
  private int chars(Message message) {
    return message.toString().length();
  }
}
