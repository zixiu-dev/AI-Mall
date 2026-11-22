package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.service.skill.SkillService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 把项目工具定义转换成 Spring AI ToolCallback。 每轮创建工具适配器，每次推理的实际开放名单由 SkillContextPolicy 决定。 */
@Component
@RequiredArgsConstructor
public class DynamicToolCatalog {

  /** 读取全部已注册工具，为每个工具创建回调。 */
  private final ToolRegistry registry;

  /** 传给每个工具回调，用来执行工具。 */
  private final ToolDispatcher dispatcher;

  /** 读取本会话已加载的技能，决定哪些工具对模型可见。 */
  private final SkillService skills;

  /** 传给每个工具回调，用来解析 Schema、参数和序列化结果。 */
  private final ObjectMapper objectMapper;

  /** 传给每个工具回调，读取工具结果的字符上限。 */
  private final AgentProperties properties;

  /** 为本轮创建 SDK 工具箱并注册适配器；注册不等于本轮全部开放，推理前还会过滤。 */
  public List<ToolCallback> callbacks(Set<String> allowed) {
    return registry.all().stream()
        .filter(t -> allowed.contains(t.getName()))
        .map(
            tool ->
                (ToolCallback)
                    new CommerceToolCallback(tool, dispatcher, skills, objectMapper, properties))
        .toList();
  }

  /** 按当前有效技能计算可见工具名，供回复调试信息查看。 */
  public List<String> visibleToolNames(String conversationId) {
    Set<String> loaded = skills.loadedSkillCodes(conversationId);
    return registry.all().stream()
        .filter(tool -> tool.getRequiredSkill() == null || loaded.contains(tool.getRequiredSkill()))
        .map(RegisteredTool::getName)
        .toList();
  }
}
