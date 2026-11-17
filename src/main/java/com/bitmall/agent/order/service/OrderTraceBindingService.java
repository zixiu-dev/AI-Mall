package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.model.CreateOrderCommand;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 首次正式提交才持久化购买内容；独立事务保留后续预扣失败和受理中断的恢复凭据。 */
@Service
public class OrderTraceBindingService {
  private final OrderTraceRepository traces;
  private final OrderSnapshotService snapshots;
  private final OrderSubmissionPolicy policy;
  private final OrderProcessingProperties properties;

  public OrderTraceBindingService(
      OrderTraceRepository traces,
      OrderSnapshotService snapshots,
      OrderSubmissionPolicy policy,
      OrderProcessingProperties properties) {
    this.traces = traces;
    this.snapshots = snapshots;
    this.policy = policy;
    this.properties = properties;
  }

  /** 独立提交绑定，后续受理回滚不会丢失购买快照；唯一 trace 索引防止并发重复绑定。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void bind(long user, String id, CreateOrderCommand command) {
    if (command.fromCart()
        && (command.getExpectedItems() == null || command.getExpectedItems().isEmpty()))
      throw new IllegalArgumentException("首次购物车提交必须携带已确认的购买明细");
    var quote = snapshots.prepare(user, command);
    String payload = snapshots.write(quote);
    traces.insert(
        id,
        user,
        null,
        policy.prepareMode(),
        payload,
        snapshots.hash(payload),
        LocalDateTime.now().plusSeconds(properties.getTraceTtlSeconds()),
        snapshots.commandHash(command));
  }
}
