package com.bitmall.agent.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.agent.dao.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import com.bitmall.agent.agent.dao.entity.ChatMessageEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {
  @Insert(
      "INSERT INTO chat_message(conversation_id,role,content,metadata,generation_status,reply_to_message_id,request_key) VALUES"
          + " (#{conversationId},#{role},#{content},#{metadata},#{generationStatus},#{replyToMessageId},#{requestKey})")
  @Options(
      useGeneratedKeys = true,
      keyProperty = "id",
      keyColumn = "id")
  int insertMessage(ChatMessageEntity message);

  int touchConversation(Map<String, Object> values);

  List<Map<String, Object>> recentMessagesSql(
      Map<String, Object> values);

  Map<String, Object> ownedAnswer(Map<String, Object> values);

  Map<String, Object> answerByRequest(Map<String, Object> values);

  int finishAnswer(Map<String, Object> values);

  int interruptUnfinishedAnswers();

  List<Integer> messageCountSql(Map<String, Object> values);

  List<String> summarySql(Map<String, Object> values);

  List<Long> selectRecentTurnCutoff(Map<String, Object> values);

  List<Map<String, Object>> selectUnsummarizedMessages(
      Map<String, Object> values);

  int updateSummarySql(Map<String, Object> values);

  List<LocalDateTime> updatedAtSql(Map<String, Object> values);
}
