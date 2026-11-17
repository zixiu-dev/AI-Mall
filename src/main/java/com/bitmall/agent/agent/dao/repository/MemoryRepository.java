package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.entity.UserMemoryDO;
import com.bitmall.agent.agent.dao.mapper.MemoryMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 用户偏好表的数据访问组件。 按用户读写 user_memory，并处理重复插入；偏好何时允许保存由工具和业务入口控制。 */
@Repository
public class MemoryRepository {

  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final MemoryMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public MemoryRepository(MemoryMapper mapper) {
    this.mapper = mapper;
  }

  /** 按用户读取购物偏好，最近更新的排在前面。 */
  public List<UserMemoryDO> findByUser(long userId) {
    return mapper.findByUserSql(parameters("userId", userId)).stream()
        .map(
            row ->
                new UserMemoryDO(
                    RowValues.string(row, "memory_key"), RowValues.string(row, "memory_value")))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
  }

  /** 先尝试更新已有偏好，不存在则插入；并发插入冲突时再更新。 */
  public void upsert(long userId, String key, String value, String conversationId) {
    int updated =
        mapper.updatePreference(
            parameters(
                "userId", userId, "key", key, "value", value, "conversationId", conversationId));
    if (updated == 1) return;
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
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
