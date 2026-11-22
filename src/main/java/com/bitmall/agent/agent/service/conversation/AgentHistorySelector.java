package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.config.AgentProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

/**
 * 决定每次调用模型时带上哪些历史消息。
 *
 * <p>以"用户轮次"为单位裁剪：一轮从一条用户消息开始，包含随后的助手回复、工具调用和工具结果。 整轮保留或整轮丢弃，不会出现只剩工具结果、却丢了对应工具调用的情况。 系统消息始终保留。
 */
@Component
@RequiredArgsConstructor
public class AgentHistorySelector {
  /** 读取历史裁剪规则：至少保留的最近轮数和上下文字符上限。 */
  private final AgentProperties properties;

  /**
   * 选出本次可见的历史。
   *
   * <p>没超过轮数和字符上限时原样返回；否则先只保留最近 N 轮，仍超过字符上限就继续丢掉最旧的一轮， 直到只剩当前这一轮还超限，则拒绝本次请求。
   *
   * @throws IllegalStateException 只保留当前一轮仍然超过字符上限
   */
  public List<Message> selectHistory(List<Message> all) {
    List<Integer> turnStarts = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).getMessageType() == MessageType.USER) {
        turnStarts.add(i);
      }
    }
    int keepRecentTurns = properties.getKeepRecentTurns();
    int maxChars = properties.getMaxContextChars();
    if (turnStarts.size() <= keepRecentTurns && totalChars(all) <= maxChars) {
      return all;
    }

    for (int firstKeptTurn = Math.max(0, turnStarts.size() - keepRecentTurns);
        firstKeptTurn < turnStarts.size();
        firstKeptTurn++) {
      List<Message> selected = keepFrom(all, turnStarts.get(firstKeptTurn));
      if (totalChars(selected) <= maxChars) {
        return selected;
      }
    }
    throw new IllegalStateException("当前任务上下文过大，请缩小查询范围后重新发起任务");
  }

  /** 保留下标 cutoff 及之后的消息，以及之前的系统消息。 */
  private static List<Message> keepFrom(List<Message> all, int cutoff) {
    List<Message> selected = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      if (i >= cutoff || all.get(i).getMessageType() == MessageType.SYSTEM) {
        selected.add(all.get(i));
      }
    }
    return selected;
  }

  /** 用消息的完整文本表示估算字符数。这只是粗略估计，不是模型的精确 token 数。 */
  private static int totalChars(List<Message> messages) {
    return messages.stream().mapToInt(message -> message.toString().length()).sum();
  }
}
