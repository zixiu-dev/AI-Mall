package com.bitmall.agent.agent.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.ChatMessageEntity;
import com.bitmall.agent.agent.dao.entity.ConversationEntity;
import com.bitmall.agent.agent.dao.mapper.ConversationMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * conversation 和 chat_message 两张表的数据访问。
 *
 * <p>负责读写会话、消息及消息附加信息（metadata JSON）。会话是否属于当前用户、何时整理摘要等业务判断由 Service 负责。
 */
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

  /** 会话与消息表的 SQL：读写消息、最近轮次、摘要和会话更新时间。 */
  private final ConversationMapper mapper;

  /** 把消息附加信息（Map）序列化成 JSON 存库，读取时再还原。 */
  private final ObjectMapper objectMapper;

  /** 只查询会话编号是否存在；是否属于当前用户由服务层另行核对。 */
  public boolean exists(String id) {
    return mapper.selectCount(
            new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getId, id))
        > 0;
  }

  /** 写入会话编号、用户和标题；并发创建同一编号时交由后续归属检查处理。 */
  public void insert(String id, long userId, String title) {
    try {
      ConversationEntity entity = new ConversationEntity();
      entity.setId(id);
      entity.setUserId(userId);
      entity.setTitle(title);
      mapper.insert(entity);
    } catch (DuplicateKeyException ignored) {
      // 并发创建同一会话后，由服务层继续核对所属用户。
    }
  }

  /** 查询指定会话是否属于当前用户。 */
  public boolean belongsTo(String id, long userId) {
    return mapper.selectCount(
            new LambdaQueryWrapper<ConversationEntity>()
                .eq(ConversationEntity::getId, id)
                .eq(ConversationEntity::getUserId, userId))
        == 1;
  }

  /** 插入消息和附加 JSON，返回生成的消息编号，并刷新会话更新时间。 */
  public long addMessage(
      String conversationId, String role, String content, Map<String, Object> metadata) {
    Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
    ChatMessageEntity entity = new ChatMessageEntity();
    entity.setConversationId(conversationId);
    entity.setRole(role);
    entity.setContent(content);
    entity.setMetadata(writeJson(safeMetadata));
    mapper.insertMessage(entity);
    if (entity.getId() == null) {
      throw new IllegalStateException("消息主键生成失败");
    }
    long messageId = entity.getId();
    mapper.touchConversation(parameters("id", conversationId));
    return messageId;
  }

  /** 返回最近 limit 条消息，按时间从旧到新排列。SQL 按最新在前取数，因此这里需要反转一次。 */
  public List<ChatMessageDO> recentMessages(String conversationId, int limit) {
    List<Map<String, Object>> newestFirstRows =
        mapper.recentMessagesSql(parameters("conversationId", conversationId, "limit", limit));
    List<ChatMessageDO> messages = toMessages(newestFirstRows);
    Collections.reverse(messages);
    return messages;
  }

  /** 统计当前会话消息条数，供摘要策略判断是否需要整理。 */
  public int messageCount(String conversationId) {
    return RowValues.required(mapper.messageCountSql(parameters("conversationId", conversationId)));
  }

  /** 读取已有摘要，空白摘要视为不存在。 */
  public Optional<String> summary(String conversationId) {
    return RowValues.optional(mapper.summarySql(parameters("id", conversationId)))
        .filter(value -> !value.isBlank());
  }

  /**
   * 返回还没进入摘要、且位于最近 keepTurns 个完整轮次之前的消息。
   *
   * <p>先查出"最近 keepTurns 轮"起点的消息编号作为分界线；分界线之前、摘要水位之后的消息才需要整理。按轮次切分可以避免把一轮问答拆开，水位可以避免重复整理。
   */
  public List<ChatMessageDO> unsummarizedBeforeRecentTurns(String conversationId, int keepTurns) {
    Optional<Long> cutoffMessageId =
        RowValues.optional(
            mapper.selectRecentTurnCutoff(
                parameters("c", conversationId, "offset", keepTurns - 1)));
    if (cutoffMessageId.isEmpty()) {
      return List.of();
    }
    return toMessages(
        mapper.selectUnsummarizedMessages(
            parameters("c", conversationId, "cutoff", cutoffMessageId.get())));
  }

  /** 只有新水位更靠后时才更新摘要，避免旧计算覆盖较新的进度。 */
  public void updateSummary(String conversationId, String summary, long untilMessageId) {
    mapper.updateSummarySql(
        parameters("summary", summary, "untilId", untilMessageId, "id", conversationId));
  }

  /** 读取会话最近更新时间。 */
  public Optional<LocalDateTime> updatedAt(String conversationId) {
    return RowValues.optional(mapper.updatedAtSql(parameters("id", conversationId)));
  }

  /** 把查询行逐条转成消息对象，返回可修改的列表（调用方可能需要反转顺序）。 */
  private List<ChatMessageDO> toMessages(List<Map<String, Object>> rows) {
    return rows.stream().map(this::toMessage).collect(Collectors.toCollection(ArrayList::new));
  }

  /** 把一行 chat_message 记录转成消息对象。 */
  private ChatMessageDO toMessage(Map<String, Object> row) {
    return new ChatMessageDO(
        RowValues.longValue(row, "id"),
        RowValues.string(row, "conversation_id"),
        RowValues.string(row, "role"),
        RowValues.string(row, "content"),
        readMap(RowValues.string(row, "metadata")),
        RowValues.timestamp(row, "created_at").toLocalDateTime());
  }

  /** 序列化消息附加信息，写入失败时明确抛出异常。 */
  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("消息元数据序列化失败", e);
    }
  }

  /** 恢复消息附加信息；内容损坏时返回空附加信息，保留正文可读性。 */
  private Map<String, Object> readMap(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  /**
   * 把 "名称, 值, 名称, 值..." 组装成 Mapper 参数 Map。
   *
   * <p>不用 Map.of 是因为它不允许 null 值，而部分可选参数可能为 null。
   */
  private static Map<String, Object> parameters(Object... namesAndValues) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      String name = (String) namesAndValues[i];
      Object value = namesAndValues[i + 1];
      values.put(name, value);
    }
    return values;
  }
}
