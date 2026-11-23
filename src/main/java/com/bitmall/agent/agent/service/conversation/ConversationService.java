package com.bitmall.agent.agent.service.conversation;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.AgentAnswerDO;
import com.bitmall.agent.agent.dao.repository.ConversationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话身份和消息访问的业务入口。 负责生成会话编号、检查用户归属、保存和读取消息；页面查历史也必须经过这里。 */
@Service
@RequiredArgsConstructor
public class ConversationService {
  /** 会话和消息的读写：创建会话、校验归属、保存消息、读取最近消息和摘要。 */
  private final ConversationRepository mapper;

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
    if (!mapper.exists(id)) {
      mapper.insert(id, userId, title);
    }
    if (!mapper.belongsTo(id, userId)) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户");
    }
    return id;
  }

  /** 查询接口只验证已存在的会话归属，避免读取未知编号时创建会话。 */
  public void requireOwned(long userId, String conversationId) {
    if (!mapper.belongsTo(conversationId, userId)) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户");
    }
  }

  /** 在事务中保存消息并刷新会话时间，返回数据库生成的消息编号。 */
  @Transactional
  public long addMessage(
      String conversationId, String role, String content, Map<String, Object> metadata) {
    return mapper.addMessage(conversationId, role, content, metadata);
  }

  /** 同一短事务保存问题和助手占位。后台线程只领取已提交的回答。 */
  @Transactional
  public AgentAnswerDO createAnswer(
      long userId, String conversationId, boolean newConversation, String question, String requestKey) {
    if (newConversation) {
      ensure(conversationId, userId, question);
    } else {
      requireOwned(userId, conversationId);
    }
    long questionId = mapper.addMessage(conversationId, "user", question, Map.of());
    long answerId = mapper.addGeneratingAnswer(conversationId, questionId, requestKey);
    return mapper.ownedAnswer(userId, answerId).orElseThrow();
  }

  @Transactional
  public void finishAnswer(
      String conversationId, long id, String status, String content, Map<String, Object> metadata) {
    mapper.finishAnswer(conversationId, id, status, content, metadata);
  }

  public Optional<AgentAnswerDO> answerByRequest(long userId, String requestKey) {
    return mapper.answerByRequest(userId, requestKey);
  }

  public AgentAnswerDO answer(long userId, long id) {
    return mapper.ownedAnswer(userId, id)
        .orElseThrow(() -> new IllegalArgumentException("回答不存在或不属于当前用户"));
  }

  /** 读取可用于模型输入的近期消息，包括本轮用户问题。 */
  public List<ChatMessageDO> modelHistory(String conversationId, int limit) {
    return mapper.recentMessages(conversationId, limit, true);
  }

  /** 读取有限条页面历史消息。 */
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
