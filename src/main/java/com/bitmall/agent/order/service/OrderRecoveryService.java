package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 恢复中断的购买和库存整理任务：争抢 trace 行锁后确定终态，再按 Lua 标志幂等补偿。 */
@Service
public class OrderRecoveryService {
  private final OrderTraceRepository traces;
  private final OrderTraceFailureService failures;
  private final InventorySettlementService inventory;
  private final OrderInventoryMapper inventoryMapper;
  private final OrderMessageRepository messages;

  public OrderRecoveryService(
      OrderTraceRepository traces,
      OrderTraceFailureService failures,
      OrderMessageRepository messages,
      InventorySettlementService inventory,
      OrderInventoryMapper inventoryMapper) {
    this.traces = traces;
    this.failures = failures;
    this.messages = messages;
    this.inventory = inventory;
    this.inventoryMapper = inventoryMapper;
  }

  /** 与建单争抢同一 trace 行锁，成功购买不会被过期扫描覆盖。 */
  @Transactional
  public void reconcile(String id) {
    reconcileLocked(traces.lock(id));
  }

  /** 按原消息和购买记录匹配死信，沿用建单和扫描使用的同一把行锁。 */
  @Transactional
  public boolean reconcileBuildDeadLetter(String traceId, String eventId) {
    var trace = traces.lockIfPresent(traceId).orElse(null);
    if (trace == null || !"ASYNC".equals(trace.getMode()) || "INIT".equals(trace.getStatus())
        || !messages.matchesBuild(eventId, traceId)) return false;
    reconcileLocked(trace);
    return true;
  }

  private void reconcileLocked(OrderTraceDO trace) {
    String id = trace.getTraceId();
    if ("INIT".equals(trace.getStatus()) && !trace.getExpiresAt().isAfter(LocalDateTime.now()))
      failures.fail(id, "EXPIRED", "确认已过期，请重新结算");
    else if ("INIT".equals(trace.getStatus())
        && "RESERVING".equals(trace.getReserveStatus())
        && inventoryMapper.staleIntent(java.util.Map.of("id", id)) > 0)
      failures.fail(id, "ADMISSION_INTERRUPTED", "库存预占后受理未完成，已安排释放，请重新购买");
    if ("PROCESSING".equals(trace.getStatus()) && "ASYNC".equals(trace.getMode())) {
      var now = LocalDateTime.now();
      if (trace.getProcessingExpiresAt() == null || !trace.getProcessingExpiresAt().isAfter(now))
        failures.fail(id, "QUEUE_TIMEOUT", "排队处理超时，本次未创建订单，请重新确认购买");
      else {
        messages.requeueBuild(id, now.minusSeconds(30));
        var next = now.plusSeconds(30);
        traces.checked(
            id,
            next.isAfter(trace.getProcessingExpiresAt()) ? trace.getProcessingExpiresAt() : next);
      }
    }
    try {
      inventory.settleLocked(traces.lock(id));
    } catch (RuntimeException retry) {
      // 业务失败状态仍提交；整理失败保留 PENDING，不把 Redis 故障变成重复接单。
      traces.checked(id, LocalDateTime.now().plusSeconds(5));
    }
  }
}
