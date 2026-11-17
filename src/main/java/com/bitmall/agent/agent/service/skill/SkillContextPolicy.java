package com.bitmall.agent.agent.service.skill;

import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.tool.ToolRegistry;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;

/** 每次推理前从可见历史计算技能权限。只有后端工具结果元数据能授权，普通文本和模型参数不能授权。 */
@Component
public class SkillContextPolicy {
  public static final String LOADED_SKILLS = "loadedSkillsByCall";
  public static final String ALLOWED_TOOLS = "allowedTools";
  private final ToolRegistry registry;
  private final SkillService skills;
  private final PendingActionService pending;

  public SkillContextPolicy(
      ToolRegistry registry, SkillService skills, PendingActionService pending) {
    this.registry = registry;
    this.skills = skills;
    this.pending = pending;
  }

  /** 先裁剪再调用本方法，防止用已经消失的手册授权；确认卡出现时收回所有工具。 */
  public Set<String> allowed(String conversation, List<Message> visible) {
    Set<String> enabled =
        skills.listEnabled().stream().map(s -> s.getCode()).collect(Collectors.toSet());
    Set<String> loaded = new HashSet<>();
    for (Message message : visible)
      if (message instanceof ToolResponseMessage t
          && t.getMetadata().get(LOADED_SKILLS) instanceof Map<?, ?> markers) {
        for (var r : t.getResponses()) {
          Object code = markers.get(r.id());
          if (code instanceof String s && enabled.contains(s)) loaded.add(s);
        }
      }
    skills.replaceLoaded(conversation, loaded);
    if (pending.latest(conversation) != null) return Set.of();
    return registry.all().stream()
        .filter(t -> t.getRequiredSkill() == null || loaded.contains(t.getRequiredSkill()))
        .map(t -> t.getName())
        .collect(Collectors.toUnmodifiableSet());
  }
}
