package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.dao.mapper.PendingActionMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/** 待确认操作表的数据访问组件。 保存原始参数和结果，通过状态与版本条件更新防止重复确认；是否允许执行由 Service 判断。 */
@Repository
@RequiredArgsConstructor
public class PendingActionRepository {

  /** 待确认操作的 SQL，定义在 mapper/agent/PendingActionMapper.xml。 */
  private final PendingActionMapper mapper;

  /** 把待确认操作的参数和执行结果（Map）序列化成 JSON 存库，读取时再还原。 */
  private final ObjectMapper objectMapper;

  /** 插入待确认操作；相同幂等键重复写入时沿用已存在的记录。 */
  public void insert(PendingActionDO action) {
    try {
      mapper.insertSql(
          parameters(
              "id",
              action.getId(),
              "key",
              action.getIdempotencyKey(),
              "userId",
              action.getUserId(),
              "conversationId",
              action.getConversationId(),
              "toolName",
              action.getToolName(),
              "arguments",
              write(action.getArguments()),
              "hash",
              action.getArgumentsHash(),
              "summary",
              action.getSummary()));
    } catch (DuplicateKeyException ignored) {
      // 相同 requestId + 工具 + 参数只生成一张确认卡。
    }
  }

  /** 按操作编号和用户编号读取确认数据。 */
  public Optional<PendingActionDO> find(String id, long userId) {
    return RowValues.optional(
        mapper.findSql(parameters("id", id, "userId", userId)).stream()
            .map(
                row ->
                    new PendingActionDO(
                        RowValues.string(row, "id"),
                        RowValues.string(row, "idempotency_key"),
                        RowValues.longValue(row, "user_id"),
                        RowValues.string(row, "conversation_id"),
                        RowValues.string(row, "tool_name"),
                        read(RowValues.string(row, "arguments")),
                        RowValues.string(row, "arguments_hash"),
                        RowValues.string(row, "summary"),
                        RowValues.string(row, "status"),
                        read(RowValues.string(row, "result_json")),
                        RowValues.intValue(row, "version"),
                        RowValues.timestamp(row, "created_at").toLocalDateTime()))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 读取会话中最新尚待确认的操作，聊天入口据此决定是否提醒用户先处理。 */
  public Optional<PendingActionDO> latestPending(String conversationId) {
    return RowValues.optional(
        mapper.latestPendingSql(parameters("conversationId", conversationId)).stream()
            .map(
                row ->
                    new PendingActionDO(
                        RowValues.string(row, "id"),
                        RowValues.string(row, "idempotency_key"),
                        RowValues.longValue(row, "user_id"),
                        RowValues.string(row, "conversation_id"),
                        RowValues.string(row, "tool_name"),
                        read(RowValues.string(row, "arguments")),
                        RowValues.string(row, "arguments_hash"),
                        RowValues.string(row, "summary"),
                        RowValues.string(row, "status"),
                        read(RowValues.string(row, "result_json")),
                        RowValues.intValue(row, "version"),
                        RowValues.timestamp(row, "created_at").toLocalDateTime()))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 查询近期已决定执行的下单确认卡，用于会话恢复时反查原购买。 */
  public List<String> recentDecidedOrderIds(long userId, String conversationId, int limit) {
    return mapper.recentDecidedOrderIds(
        parameters("userId", userId, "conversationId", conversationId, "limit", limit));
  }

  /** 只把仍为 PENDING 的记录改成批准或拒绝，并核对更新条数，阻止重复确认。 */
  public PendingActionDO decide(String id, long userId, boolean approved) {
    int updated =
        mapper.decideSql(
            parameters("status", approved ? "APPROVED" : "REJECTED", "id", id, "userId", userId));
    if (updated != 1) {
      throw new IllegalStateException("该操作已经处理或不存在");
    }
    return find(id, userId).orElseThrow();
  }

  /** 只有状态和版本仍符合预期时，才标记业务执行成功并保存结果。 */
  public void markExecuted(String id, int expectedVersion, Map<String, Object> result) {
    int updated =
        mapper.markExecutedSql(
            parameters("result", write(result), "id", id, "version", expectedVersion));
    if (updated != 1) {
      throw new IllegalStateException("操作状态已经变化，未重复执行");
    }
  }

  /** 按状态和版本条件记录执行失败，避免覆盖已经完成的记录。 */
  public void markFailed(String id, int expectedVersion, Map<String, Object> result) {
    mapper.markFailedSql(parameters("result", write(result), "id", id, "version", expectedVersion));
  }

  /** 把操作参数或结果序列化为 JSON 存入数据库。 */
  private String write(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("待确认参数序列化失败", e);
    }
  }

  /** 把数据库 JSON 恢复成参数或结果 Map。 */
  private Map<String, Object> read(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
