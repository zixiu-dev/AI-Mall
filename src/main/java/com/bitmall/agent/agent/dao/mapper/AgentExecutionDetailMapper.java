package com.bitmall.agent.agent.dao.mapper;

import com.bitmall.agent.agent.dao.entity.AgentExecutionDetailDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 写入不含正文的模型与工具明细，按轮查询或按时间定量回收。 */
@Mapper
public interface AgentExecutionDetailMapper {
  @Insert("""
      <script>
      INSERT INTO agent_model_call(turn_id, sequence_no, step_no, attempt_no, candidate, model,
          status, error_code, started_at, duration_ms, first_text_ms, input_tokens, output_tokens)
      VALUES
      <foreach collection="rows" item="r" separator=",">
        (#{r.turnId}, #{r.sequenceNo}, #{r.stepNo}, #{r.attemptNo}, #{r.candidate}, #{r.model},
         #{r.status}, #{r.errorCode}, #{r.startedAt}, #{r.durationMs}, #{r.firstTextMs},
         #{r.inputTokens}, #{r.outputTokens})
      </foreach>
      </script>
      """)
  int insertModelCalls(@Param("rows") List<AgentExecutionDetailDO> rows);

  @Insert("""
      <script>
      INSERT INTO agent_turn_item(turn_id, sequence_no, step_no, item_type, model_call_sequence,
          candidate, tool_call_id, tool_name, execution_count, action_id, status, error_code,
          started_at, duration_ms)
      VALUES
      <foreach collection="rows" item="r" separator=",">
        (#{r.turnId}, #{r.sequenceNo}, #{r.stepNo}, #{r.itemType}, #{r.modelCallSequence},
         #{r.candidate}, #{r.toolCallId}, #{r.toolName}, #{r.executionCount}, #{r.actionId},
         #{r.status}, #{r.errorCode}, #{r.startedAt}, #{r.durationMs})
      </foreach>
      </script>
      """)
  int insertItems(@Param("rows") List<AgentExecutionDetailDO> rows);

  @Select("""
      SELECT turn_id, sequence_no, step_no, 'MODEL_CALL' AS item_type, attempt_no, candidate, model,
          status, error_code, started_at, duration_ms, first_text_ms, input_tokens, output_tokens
      FROM agent_model_call WHERE turn_id = #{turnId} AND started_at >= #{cutoff}
      ORDER BY sequence_no
      """)
  List<AgentExecutionDetailDO> modelCalls(@Param("turnId") long turnId, @Param("cutoff") LocalDateTime cutoff);

  @Select("""
      SELECT turn_id, sequence_no, step_no, item_type, model_call_sequence, candidate, tool_call_id,
          tool_name, execution_count, action_id, status, error_code, started_at, duration_ms
      FROM agent_turn_item WHERE turn_id = #{turnId} AND started_at >= #{cutoff}
      ORDER BY sequence_no
      """)
  List<AgentExecutionDetailDO> items(@Param("turnId") long turnId, @Param("cutoff") LocalDateTime cutoff);

  @Select("SELECT id FROM agent_model_call WHERE started_at < #{cutoff} ORDER BY started_at, id LIMIT #{limit}")
  List<Long> expiredModelCalls(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  @Select("SELECT id FROM agent_turn_item WHERE started_at < #{cutoff} ORDER BY started_at, id LIMIT #{limit}")
  List<Long> expiredItems(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  @Delete("""
      <script>DELETE FROM agent_model_call WHERE id IN
      <foreach collection="ids" item="id" separator="," open="(" close=")">#{id}</foreach>
      </script>
      """)
  int deleteModelCalls(@Param("ids") List<Long> ids);

  @Delete("""
      <script>DELETE FROM agent_turn_item WHERE id IN
      <foreach collection="ids" item="id" separator="," open="(" close=")">#{id}</foreach>
      </script>
      """)
  int deleteItems(@Param("ids") List<Long> ids);
}
