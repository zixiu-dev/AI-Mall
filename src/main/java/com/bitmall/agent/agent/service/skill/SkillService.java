package com.bitmall.agent.agent.service.skill;

import com.bitmall.agent.agent.dao.entity.SkillDO;
import com.bitmall.agent.agent.dao.repository.SkillRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 业务技能目录和会话加载状态的入口。 加载时核对技能是否启用，随后记录会话已加载技能；上下文裁剪后会重新同步有效技能。 */
@Service
public class SkillService {
  /** 依赖组件：技能目录和会话技能关联表的数据访问组件。 */
  private final SkillRepository mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public SkillService(SkillRepository mapper) {
    this.mapper = mapper;
  }

  /** 读取启用的业务手册目录，作为模型可查询的技能介绍。 */
  public List<SkillDO> listEnabled() {
    return mapper.listEnabled();
  }

  /** 查找仍启用的技能，停用或不存在的技能不返回。 */
  public Optional<SkillDO> find(String code) {
    return mapper.find(code);
  }

  /** 读取当前会话记录的已加载技能代码。 */
  public Set<String> loadedSkillCodes(String conversationId) {
    return mapper.loadedSkillCodes(conversationId);
  }

  /** 为会话记录一个技能已加载，重复加载保持幂等。 */
  public void markLoaded(String conversationId, String code) {
    mapper.markLoaded(conversationId, code);
  }

  /** 在事务中同步当前上下文实际保留的技能，手册被裁剪后相应权限也被收回。 */
  @Transactional
  public void replaceLoaded(String conversationId, Set<String> codes) {
    mapper.replaceLoaded(conversationId, codes);
  }

  /** 先核对技能有效，再记录加载状态并返回完整手册。 */
  public SkillDO load(String conversationId, String code) {
    SkillDO skill = find(code).orElseThrow(() -> new IllegalArgumentException("技能不存在或未启用"));
    markLoaded(conversationId, skill.getCode());
    return skill;
  }
}
