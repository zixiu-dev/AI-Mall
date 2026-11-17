package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.repository.ConversationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话身份和消息访问的业务入口。 负责生成会话编号、检查用户归属、保存和读取消息；页面查历史也必须经过这里。 */
@Service
public class ConversationService {
  /** 依赖组件：会话和消息表的数据访问组件。 */
  private final ConversationRepository mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ConversationService(ConversationRepository mapper) {
    this.mapper = mapper;
  }

  /** 缺少编号时创建会话；已有编号必须核对所属用户，标题从首条消息截取。 */
  public String ensure(String requestedId, long userId, String firstMessage) {
    String id =
        requestedId == null || requestedId.isBlank()
            ? UUID.randomUUID().toString().replace("-", "")
            : requestedId;
    String title =
        firstMessage == null || firstMessage.isBlank()
            ? "新会话"
            : firstMessage.substring(0, Math.min(30, firstMessage.length()));
    if (!mapper.exists(id)) mapper.insert(id, userId, title);
    if (!mapper.belongsTo(id, userId)) throw new IllegalArgumentException("会话不存在或不属于当前用户");
    return id;
  }

  /** 在事务中保存消息并刷新会话时间，返回数据库生成的消息编号。 */
  @Transactional
  public long addMessage(
      String conversationId, String role, String content, Map<String, Object> metadata) {
    return mapper.addMessage(conversationId, role, content, metadata);
  }

  /** 读取有限条历史消息，供页面展示或本轮模型输入使用。 */
  public List<ChatMessageDO> recentMessages(String conversationId, int limit) {
    return mapper.recentMessages(conversationId, limit);
  }

  /** 读取已经整理的较早会话摘要。 */
  public Optional<String> summary(String conversationId) {
    return mapper.summary(conversationId);
  }

  /** 先检查会话归属，再返回页面需要的最近消息集合。 */
  public Map<String, Object> messages(long userId, String conversationId) {
    ensure(conversationId, userId, null);
    return Map.of(
        "conversationId", conversationId, "messages", recentMessages(conversationId, 100));
  }
}
