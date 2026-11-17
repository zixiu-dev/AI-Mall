package com.bitmall.agent.agent.service.action;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.tool.ToolDispatcher;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 确认操作的事务边界。同步建单和操作 EXECUTED 标记一起提交，失败时一起回滚。 */
@Service
public class ConfirmedActionExecutor {
  /** 依赖组件：工具调用的统一分发入口。 */
  private final ToolDispatcher dispatcher;

  /** 依赖组件：管理待确认操作。 */
  private final PendingActionService actions;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ConfirmedActionExecutor(ToolDispatcher dispatcher, PendingActionService actions) {
    this.dispatcher = dispatcher;
    this.actions = actions;
  }

  // EXECUTED 表示确认工具已执行；订单为待支付，不代表已经扣款。
  /** 在同一个事务中完成写工具并标记 EXECUTED，失败时两者一起回滚。 */
  @Transactional
  public Map<String, Object> execute(PendingActionDO action) {
    Map<String, Object> result = dispatcher.executeConfirmed(action);
    actions.executed(action, result);
    return result;
  }
}
