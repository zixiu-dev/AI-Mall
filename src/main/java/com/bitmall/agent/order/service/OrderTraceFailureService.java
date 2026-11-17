package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementEvent;
import com.bitmall.agent.order.monitor.OrderMetrics;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 交易事务回滚之后单独记录最终业务失败，防止失败记录跟着原事务一起回滚。 */
@Service
public class OrderTraceFailureService {
  /** 最终失败只在实际提交后统计。 */
  private final OrderMetrics metrics;

  /** 失败状态和补偿意图存储。 */
  private final OrderTraceRepository traces;

  private final OrderMessageRepository messages;

  private final ApplicationEventPublisher events;

  /** 注入 trace 存储。 */
  public OrderTraceFailureService(
      OrderTraceRepository traces,
      OrderMetrics metrics,
      OrderMessageRepository messages,
      ApplicationEventPublisher events) {
    this.traces = traces;
    this.metrics = metrics;
    this.messages = messages;
    this.events = events;
  }

  /** 行锁与版本条件防止失败处理覆盖另一个实例已经提交的成功结果。 */
  @Transactional
  public void fail(String id, String code, String reason) {
    var trace = traces.lock(id);
    if ("INIT".equals(trace.getStatus()) || "PROCESSING".equals(trace.getStatus())) {
      traces.fail(trace, code, reason);
      if ("ASYNC".equals(trace.getMode())) messages.cancelBuild(id);
      metrics.committed("failed", trace.getMode());
      events.publishEvent(new InventorySettlementEvent(id, null));
    }
  }
}
