package com.bitmall.agent.agent.service.action;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.service.tool.ToolDispatcher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 确认操作的事务边界。同步建单和操作 EXECUTED 标记一起提交，失败时一起回滚。 */
@Service
@RequiredArgsConstructor
public class ConfirmedActionExecutor {
  /** 按工具名执行用户已确认的写操作。 */
  private final ToolDispatcher dispatcher;

  /** 执行成功后把待确认操作标记为已执行并保存结果。 */
  private final PendingActionService actions;

  // EXECUTED 表示确认工具已执行；订单为待支付，不代表已经扣款。
  /** 在同一个事务中完成写工具并标记 EXECUTED，失败时两者一起回滚。 */
  @Transactional
  public Map<String, Object> execute(PendingActionDO action) {
    Map<String, Object> result = dispatcher.executeConfirmed(action);
    actions.executed(action, result);
    return result;
  }
}
