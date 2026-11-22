package com.bitmall.agent.agent.dao.repository;

import com.bitmall.agent.agent.dao.entity.SkillDO;
import com.bitmall.agent.agent.dao.mapper.SkillMapper;
import com.bitmall.agent.common.persistence.RowValues;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 技能目录和会话技能关联表的数据访问组件。 读取 agent_skill，并维护 conversation_skill；每轮权限如何计算在 SkillContextPolicy 中处理。 */
@Repository
@RequiredArgsConstructor
public class SkillRepository {

  /** 技能手册和会话技能的 SQL，定义在 mapper/agent/SkillMapper.xml。 */
  private final SkillMapper mapper;

  /** 解析技能记录里以 JSON 存储的工具列表。 */
  private final ObjectMapper objectMapper;

  /** 读取当前启用的技能目录和手册。 */
  public List<SkillDO> listEnabled() {
    return mapper.listEnabledSql(Map.of()).stream()
        .map(
            row ->
                new SkillDO(
                    RowValues.string(row, "code"),
                    RowValues.string(row, "name"),
                    RowValues.string(row, "description"),
                    RowValues.string(row, "manual"),
                    readTools(RowValues.string(row, "tool_names"))))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** 按技能代码查询启用的手册，已停用技能不会返回。 */
  public Optional<SkillDO> find(String code) {
    return RowValues.optional(
        mapper.findSql(parameters("code", code)).stream()
            .map(
                row ->
                    new SkillDO(
                        RowValues.string(row, "code"),
                        RowValues.string(row, "name"),
                        RowValues.string(row, "description"),
                        RowValues.string(row, "manual"),
                        readTools(RowValues.string(row, "tool_names"))))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 查询某个会话已记录加载的技能代码集合。 */
  public Set<String> loadedSkillCodes(String conversationId) {
    return Set.copyOf(mapper.loadedSkillCodesSql(parameters("conversationId", conversationId)));
  }

  /** 记录会话加载了某个技能，重复记录按幂等方式处理。 */
  public void markLoaded(String conversationId, String skillCode) {
    try {
      mapper.markLoadedSql(parameters("conversationId", conversationId, "skillCode", skillCode));
    } catch (DuplicateKeyException ignored) {
      // 幂等加载。
    }
  }

  /** 用本轮实际保留在上下文中的技能替换旧集合；外层技能服务负责事务。 */
  public void replaceLoaded(String conversationId, Set<String> loaded) {
    if (loadedSkillCodes(conversationId).equals(loaded)) {
      return;
    }
    mapper.replaceLoadedSql(parameters("id", conversationId));
    loaded.forEach(code -> markLoaded(conversationId, code));
  }

  /** 把数据库中的关联工具 JSON 数组恢复为名称列表。 */
  private List<String> readTools(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("技能工具列表损坏", e);
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
