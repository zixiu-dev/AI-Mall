package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementEvent;
import com.bitmall.agent.order.monitor.OrderMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;

/** 交易事务回滚之后单独记录最终业务失败，防止失败记录跟着原事务一起回滚。 */
@Service
@RequiredArgsConstructor
public class OrderTraceFailureService {
  /** 锁定 trace 并写入失败状态和原因。 */
  private final OrderTraceRepository traces;

  /** 记录购买失败数。 */
  private final OrderMetrics metrics;

  /** 取消尚未执行的建单消息，失败的购买不再被建单。 */
  private final OrderMessageRepository messages;

  /** 事务提交后触发库存预占释放。 */
  private final ApplicationEventPublisher events;

  /** 行锁与版本条件防止失败处理覆盖另一个实例已经提交的成功结果。 */
  @Transactional
  public void fail(String id, String code, String reason) {
    OrderTraceDO trace = traces.lock(id);
    if ("INIT".equals(trace.getStatus()) || "PROCESSING".equals(trace.getStatus())) {
      traces.fail(trace, code, reason);
      if ("ASYNC".equals(trace.getMode())) {
        messages.cancelBuild(id);
      }
      metrics.committed("failed", trace.getMode());
      events.publishEvent(new InventorySettlementEvent(id, null));
    }
  }
}
