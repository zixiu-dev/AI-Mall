package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单相关事务提交后，尽快异步整理 Redis 库存预占（确认、释放或关单归还）。
 *
 * <p>这里只是"尽快"，不保证一定成功：失败时只记日志。整理进度保存在数据库里，没完成的会由恢复任务继续重试， 不会因为整理失败而影响已经提交的订单。
 */
@Component
@ConditionalOnProperty(name = "app.inventory.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InventorySettlementListener {
  private static final Logger log = LoggerFactory.getLogger(InventorySettlementListener.class);

  /** 执行具体的预占整理（按 trace 确认/释放，或按订单逐个 SKU 归还）。 */
  private final InventorySettlementService settlement;

  /** 查询某个订单的关单归还任务列表。 */
  private final OrderInventoryMapper mapper;

  /** 事件带 traceId 时整理这笔购买的预占；只带 orderId 时（关单），逐个 SKU 执行归还任务。 */
  @Async("inventorySettlementExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(InventorySettlementEvent event) {
    try {
      if (event.getTraceId() != null) {
        settlement.settleTrace(event.getTraceId());
      } else {
        settleOrderReturns(event.getOrderId());
      }
    } catch (RuntimeException ex) {
      log.warn("库存整理延后恢复，trace={},order={}", event.getTraceId(), event.getOrderId(), ex);
    }
  }

  private void settleOrderReturns(String orderId) {
    for (Map<String, Object> task : mapper.returnsForOrder(Map.of("order", orderId))) {
      // 防御性检查：只处理确实属于这个订单的任务行。
      if (orderId.equals(RowValues.string(task, "order_id"))) {
        settlement.settleReturn(orderId, RowValues.longValue(task, "sku_id"));
      }
    }
  }
}
