package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** 提交后尽快确认或返还；异常只记录，持久化进度由恢复任务重试，不推翻已提交订单。 */
@Component
@ConditionalOnProperty(name = "app.inventory.enabled", havingValue = "true")
public class InventorySettlementListener {
  private static final Logger log = LoggerFactory.getLogger(InventorySettlementListener.class);
  private final InventorySettlementService settlement;
  private final OrderInventoryMapper mapper;

  public InventorySettlementListener(
      InventorySettlementService settlement, OrderInventoryMapper mapper) {
    this.settlement = settlement;
    this.mapper = mapper;
  }

  @Async("inventorySettlementExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(InventorySettlementEvent event) {
    try {
      if (event.getTraceId() != null) settlement.settleTrace(event.getTraceId());
      else
        for (var row : mapper.returnsForOrder(java.util.Map.of("order", event.getOrderId())))
          if (event.getOrderId().equals(RowValues.string(row, "order_id")))
            settlement.settleReturn(event.getOrderId(), RowValues.longValue(row, "sku_id"));
    } catch (RuntimeException ex) {
      log.warn("库存整理延后恢复，trace={},order={}", event.getTraceId(), event.getOrderId(), ex);
    }
  }
}
