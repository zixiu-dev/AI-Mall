package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.entity.UserMemoryDO;
import com.bitmall.agent.agent.dao.mapper.MemoryMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户偏好表的数据访问组件。 按用户读写 user_memory，并处理重复插入；偏好何时允许保存由工具和业务入口控制。 */
@Repository
@RequiredArgsConstructor
public class MemoryRepository {

  /** 用户长期偏好的 SQL，定义在 mapper/agent/MemoryMapper.xml。 */
  private final MemoryMapper mapper;

  /** 按用户读取购物偏好，最近更新的排在前面。 */
  public List<UserMemoryDO> findByUser(long userId) {
    return mapper.findByUserSql(parameters("userId", userId)).stream()
        .map(
            row ->
                new UserMemoryDO(
                    RowValues.string(row, "memory_key"), RowValues.string(row, "memory_value")))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** 先尝试更新已有偏好，不存在则插入；并发插入冲突时再更新。 */
  public void upsert(long userId, String key, String value, String conversationId) {
    int updated =
        mapper.updatePreference(
            parameters(
                "userId", userId, "key", key, "value", value, "conversationId", conversationId));
    if (updated == 1) {
      return;
    }
    try {
      mapper.insertPreference(
          parameters(
              "userId", userId, "key", key, "value", value, "conversationId", conversationId));
    } catch (DuplicateKeyException race) {
      mapper.updatePreference(
          parameters(
              "userId", userId, "key", key, "value", value, "conversationId", conversationId));
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
