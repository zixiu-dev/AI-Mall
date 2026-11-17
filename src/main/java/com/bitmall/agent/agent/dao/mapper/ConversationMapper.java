package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {
  @org.apache.ibatis.annotations.Insert(
      "INSERT INTO chat_message(conversation_id,role,content,metadata) VALUES"
          + " (#{conversationId},#{role},#{content},#{metadata})")
  @org.apache.ibatis.annotations.Options(
      useGeneratedKeys = true,
      keyProperty = "id",
      keyColumn = "id")
  int insertMessage(com.bitmall.agent.agent.dao.entity.ChatMessageEntity message);

  int touchConversation(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> recentMessagesSql(
      java.util.Map<String, Object> values);

  java.util.List<Integer> messageCountSql(java.util.Map<String, Object> values);

  java.util.List<String> summarySql(java.util.Map<String, Object> values);

  java.util.List<Long> selectRecentTurnCutoff(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectUnsummarizedMessages(
      java.util.Map<String, Object> values);

  int updateSummarySql(java.util.Map<String, Object> values);

  java.util.List<java.time.LocalDateTime> updatedAtSql(java.util.Map<String, Object> values);
}
