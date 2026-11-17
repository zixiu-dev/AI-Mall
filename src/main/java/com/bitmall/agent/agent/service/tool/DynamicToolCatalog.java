package com.bitmall.agent.agent.service.tool;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.service.skill.SkillService;
import java.util.List;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 把项目工具定义转换成 Spring AI ToolCallback。 每轮创建工具适配器，每次推理的实际开放名单由 SkillContextPolicy 决定。 */
@Component
public class DynamicToolCatalog {

  /** 依赖组件：聚合所有 ToolProvider 的工具目录。 */
  private final ToolRegistry registry;

  /** 依赖组件：工具调用的统一分发入口。 */
  private final ToolDispatcher dispatcher;

  /** 依赖组件：业务技能目录和会话加载状态的入口。 */
  private final SkillService skills;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper objectMapper;

  /** 依赖组件：读取 application.yml 中 app.agent 配置。 */
  private final AgentProperties properties;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public DynamicToolCatalog(
      ToolRegistry registry,
      ToolDispatcher dispatcher,
      SkillService skills,
      ObjectMapper objectMapper,
      AgentProperties properties) {
    this.registry = registry;
    this.dispatcher = dispatcher;
    this.skills = skills;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

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
