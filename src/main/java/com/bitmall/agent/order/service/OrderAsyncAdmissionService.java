package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.monitor.OrderMetrics;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排队受理短事务：核对已经完成的 Redis 预占，保存处理状态和消息；优惠券由建单事务锁定。 */
@Service
public class OrderAsyncAdmissionService {
  private final OrderTraceRepository traces;
  private final OrderMessageService messages;
  private final OrderSubmissionPolicy policy;
  private final OrderProcessingProperties properties;
  private final OrderMetrics metrics;
  private final OrderInventoryService inventory;

  public OrderAsyncAdmissionService(
      OrderTraceRepository traces,
      OrderMessageService messages,
      OrderSubmissionPolicy policy,
      OrderProcessingProperties properties,
      OrderMetrics metrics,
      OrderInventoryService inventory) {
    this.traces = traces;
    this.messages = messages;
    this.policy = policy;
    this.properties = properties;
    this.metrics = metrics;
    this.inventory = inventory;
  }

  /** trace PROCESSING 与 ORDER_BUILD 命令一起提交；外层 Agent 确认事务存在时加入该事务。 */
  @Transactional
  public void admit(long user, String id) {
    var trace = traces.lock(id);
    if (trace.getUserId() != user) throw new IllegalArgumentException("下单任务不存在或不属于当前用户");
    if (!"INIT".equals(trace.getStatus())) {
      metrics.event("repeat_submit");
      return;
    }
    if (!"ASYNC".equals(trace.getMode())) throw new IllegalArgumentException("该购买凭证不是排队下单");
    if (!trace.getExpiresAt().isAfter(LocalDateTime.now()))
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    policy.requireQueueEnabled();
    var deadline = LocalDateTime.now().plusSeconds(properties.getAsyncTimeoutSeconds());
    if (!traces.claim(trace, deadline)) return;
    inventory.verifyPrepared(traces.lock(id));
    inventory.admitted(id);
    messages.enqueueBuild(id);
    metrics.committed("accepted", "ASYNC");
  }
}
