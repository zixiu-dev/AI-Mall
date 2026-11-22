package com.bitmall.agent.agent.service.skill;

import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.tool.ToolRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

/**
 * 每次调用模型前，计算本轮允许使用哪些工具。
 *
 * <p>需要技能的工具，只有对应技能手册还留在本轮可见上下文里才开放。"技能已加载"只认后端写在 load_skill 工具结果元数据里的标记， 模型在文字里声称加载了、或在参数里伪造，都不算。
 */
@Component
@RequiredArgsConstructor
public class SkillContextPolicy {
  /** load_skill 工具结果的元数据键：工具调用编号 → 加载的技能代码。 */
  public static final String LOADED_SKILLS = "loadedSkillsByCall";

  /** ToolContext 里存放本轮允许工具名集合的键。 */
  public static final String ALLOWED_TOOLS = "allowedTools";

  /** 读取全部已注册工具，按已加载技能筛选本轮可见的工具。 */
  private final ToolRegistry registry;

  /** 读取启用的技能列表；按本轮上下文中实际保留的技能更新"已加载技能"。 */
  private final SkillService skills;

  /** 会话有待确认操作时，本轮不开放任何工具，先等用户处理确认卡。 */
  private final PendingActionService pending;

  /**
   * 返回本轮允许的工具名。
   *
   * <p>visible 必须是已经裁剪过的历史：手册所在的消息被裁掉后，它授予的工具也随之收回。 会话有待确认操作时返回空集合。
   */
  public Set<String> allowed(String conversation, List<Message> visible) {
    Set<String> loaded = loadedSkillsIn(visible);
    skills.replaceLoaded(conversation, loaded);
    if (pending.latest(conversation) != null) {
      return Set.of();
    }
    return registry.all().stream()
        .filter(tool -> tool.getRequiredSkill() == null || loaded.contains(tool.getRequiredSkill()))
        .map(tool -> tool.getName())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 从可见消息里找出由后端标记、且当前仍启用的技能。 */
  private Set<String> loadedSkillsIn(List<Message> visible) {
    Set<String> enabled =
        skills.listEnabled().stream().map(skill -> skill.getCode()).collect(Collectors.toSet());
    Set<String> loaded = new HashSet<>();
    for (Message message : visible) {
      if (!(message instanceof ToolResponseMessage toolResults)) {
        continue;
      }
      if (!(toolResults.getMetadata().get(LOADED_SKILLS) instanceof Map<?, ?> skillByCallId)) {
        continue;
      }
      for (ToolResponseMessage.ToolResponse response : toolResults.getResponses()) {
        Object code = skillByCallId.get(response.id());
        if (code instanceof String skillCode && enabled.contains(skillCode)) {
          loaded.add(skillCode);
        }
      }
    }
    return loaded;
  }
}
