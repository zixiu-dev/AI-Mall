package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.repository.ConversationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 整理较早的聊天内容。 按水位只处理尚未摘要的旧消息，并保留最近完整轮次；当前实现是截取和拼接文字，不调用模型生成语义摘要。 */
@Service
public class ConversationSummaryService {

  /** 旧会话摘要的字符上限，避免摘要本身无限占用模型上下文。 */
  private static final int MAX_SUMMARY_CHARS = 3500;

  /** 依赖组件：会话和消息表的数据访问组件。 */
  private final ConversationRepository conversations;

  /** 依赖组件：读取 application.yml 中 app.agent 配置。 */
  private final AgentProperties properties;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ConversationSummaryService(
      ConversationRepository conversations, AgentProperties properties) {
    this.conversations = conversations;
    this.properties = properties;
  }

  /** 消息数达到阈值后，只摘录最近保留轮次之前且超过水位的内容，限制摘要长度并推进水位。 */
  public void compactIfNeeded(String conversationId) {
    int count = conversations.messageCount(conversationId);
    if (count < properties.getSummaryTriggerCount()) {
      return;
    }
    // 最近轮次保留原文；只有较早且超过摘要水位的消息才进入本次摘录。
    List<ChatMessageDO> material =
        conversations.unsummarizedBeforeRecentTurns(
            conversationId, properties.getKeepRecentTurns());
    if (material.isEmpty()) return;
    StringBuilder summary = new StringBuilder(conversations.summary(conversationId).orElse(""));
    if (!summary.isEmpty()) summary.append("\n--- 新增进展 ---\n");
    for (ChatMessageDO message : material) {
      String role =
          switch (message.getRole()) {
            case "user" -> "用户";
            case "assistant" -> "助手";
            default -> "工具";
          };
      String content = message.getContent().replaceAll("\\s+", " ").trim();
      if (content.length() > 500) content = content.substring(0, 500) + "…";
      summary.append(role).append("：").append(content).append('\n');
    }
    if (summary.length() > MAX_SUMMARY_CHARS) {
      summary.delete(0, summary.length() - MAX_SUMMARY_CHARS);
    }
    conversations.updateSummary(
        conversationId, summary.toString(), material.get(material.size() - 1).getId());
  }
}
