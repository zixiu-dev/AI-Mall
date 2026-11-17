package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.mapper.AgentStateMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 版本化 Agent 消息状态的数据访问组件。 按用户、会话和状态键保存 JSON 字符串，不依赖模型框架类型，序列化由上层适配器完成。 */
@Repository
public class AgentStateRepository {
  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final AgentStateMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AgentStateRepository(AgentStateMapper mapper) {
    this.mapper = mapper;
  }

  /** 先更新已有状态，不存在再插入；并发创建同一状态时重新更新。 */
  public void put(String user, String session, String key, String payload) {
    if (update(user, session, key, payload) == 0) {
      try {
        mapper.putSql(parameters("u", safe(user), "s", session, "k", key, "p", payload));
      } catch (DuplicateKeyException e) {
        update(user, session, key, payload);
      }
    }
  }

  /** 按用户、会话和状态键更新序列化内容。 */
  private int update(String user, String session, String key, String payload) {
    return mapper.updateSql(parameters("u", safe(user), "s", session, "k", key, "p", payload));
  }

  /** 按用户、会话和状态键读取 JSON；Mapper 不解析模型 SDK 对象。 */
  public Optional<String> payload(String user, String session, String key) {
    return RowValues.optional(
        mapper.payloadSql(parameters("u", safe(user), "s", session, "k", key)));
  }

  /** 查询指定用户的某个会话是否保存过原生状态。 */
  public boolean exists(String user, String session) {
    return RowValues.required(mapper.existsSql(parameters("u", safe(user), "s", session))) > 0;
  }

  /** 列出指定用户保存过状态的会话编号。 */
  public Set<String> listSessionIds(String user) {
    return new LinkedHashSet<>(mapper.listSessionIdsSql(parameters("u", safe(user))));
  }

  /** 把框架允许的空用户编号转换为数据库可保存的匿名标识。 */
  private String safe(String user) {
    return user == null ? "__anon__" : user;
  }

  /** 按用户和会话删除原生状态；带 key 的重载只删除指定状态项。 */
  public void delete(String user, String session) {
    mapper.deleteSession(parameters("u", safe(user), "s", session));
  }

  /** 按用户和会话删除原生状态；带 key 的重载只删除指定状态项。 */
  public void delete(String user, String session, String key) {
    mapper.deleteState(parameters("u", safe(user), "s", session, "k", key));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
