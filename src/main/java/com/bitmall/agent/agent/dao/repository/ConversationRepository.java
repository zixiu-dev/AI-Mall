package com.bitmall.agent.agent.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.ChatMessageEntity;
import com.bitmall.agent.agent.dao.entity.ConversationEntity;
import com.bitmall.agent.agent.dao.mapper.ConversationMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 会话和消息表的数据访问组件。 查询 conversation、chat_message，处理消息 JSON 和生成主键；用户归属及摘要策略在 Service 中协调。 */
@Repository
public class ConversationRepository {

  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final ConversationMapper mapper;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper objectMapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ConversationRepository(ConversationMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;

    this.objectMapper = objectMapper;
  }

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
    String json = writeJson(metadata == null ? Map.of() : metadata);
    ChatMessageEntity entity = new ChatMessageEntity();
    entity.setConversationId(conversationId);
    entity.setRole(role);
    entity.setContent(content);
    entity.setMetadata(json);
    mapper.insertMessage(entity);
    if (entity.getId() == null) throw new IllegalStateException("消息主键生成失败");
    long id = entity.getId();
    mapper.touchConversation(parameters("id", conversationId));
    return id;
  }

  /** 先按最新消息取有限条记录，再反转为正常阅读顺序。 */
  public List<ChatMessageDO> recentMessages(String conversationId, int limit) {
    List<ChatMessageDO> reversed =
        mapper
            .recentMessagesSql(parameters("conversationId", conversationId, "limit", limit))
            .stream()
            .map(
                row ->
                    new ChatMessageDO(
                        RowValues.longValue(row, "id"),
                        RowValues.string(row, "conversation_id"),
                        RowValues.string(row, "role"),
                        RowValues.string(row, "content"),
                        readMap(RowValues.string(row, "metadata")),
                        RowValues.timestamp(row, "created_at").toLocalDateTime()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    Collections.reverse(reversed);
    return reversed;
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

  /** 找到最近完整轮次之前、且超过摘要水位的消息，避免拆轮次或重复处理。 */
  public List<ChatMessageDO> unsummarizedBeforeRecentTurns(String conversationId, int keepTurns) {
    Optional<Long> cutoff =
        RowValues.optional(
            mapper.selectRecentTurnCutoff(
                parameters("c", conversationId, "offset", keepTurns - 1)));
    if (cutoff.isEmpty()) return List.of();
    return mapper
        .selectUnsummarizedMessages(parameters("c", conversationId, "cutoff", cutoff.get()))
        .stream()
        .map(
            row ->
                new ChatMessageDO(
                    RowValues.longValue(row, "id"),
                    RowValues.string(row, "conversation_id"),
                    RowValues.string(row, "role"),
                    RowValues.string(row, "content"),
                    readMap(RowValues.string(row, "metadata")),
                    RowValues.timestamp(row, "created_at").toLocalDateTime()))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
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

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
