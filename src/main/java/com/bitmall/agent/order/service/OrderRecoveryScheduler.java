package com.bitmall.agent.order.service;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementService;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期关闭到期待支付订单并回收过期购买凭证。通过另一个 Service 的代理开启每条任务的事务。 */
@Component
public class OrderRecoveryScheduler {
  /** 按 next_check_at 分页扫描，避免旧任务饿死新任务。 */
  private final OrderTraceRepository traces;

  /** 每条任务独立恢复事务。 */
  private final OrderRecoveryService recovery;

  private final OrderRepository orders;
  private final OrderLifecycleService lifecycle;
  private final OrderInventoryMapper inventoryMapper;
  private final InventorySettlementService inventory;

  /** 失败只记录编号，下一轮继续。 */
  private static final Logger log = LoggerFactory.getLogger(OrderRecoveryScheduler.class);

  /** 注入扫描与恢复组件。 */
  public OrderRecoveryScheduler(
      OrderTraceRepository traces,
      OrderRecoveryService recovery,
      OrderRepository orders,
      OrderLifecycleService lifecycle,
      OrderInventoryMapper inventoryMapper,
      InventorySettlementService inventory) {
    this.traces = traces;
    this.recovery = recovery;
    this.orders = orders;
    this.lifecycle = lifecycle;
    this.inventoryMapper = inventoryMapper;
    this.inventory = inventory;
  }

  /** 后台恢复不删除失败任务，也不把超时直接等同于订单失败。 */
  @Scheduled(fixedDelayString = "${app.order-processing.recovery-interval-ms:5000}")
  public void recover() {
    for (var task : inventoryMapper.pendingReturns())
      try {
        inventory.settleReturn(
            RowValues.string(task, "order_id"), RowValues.longValue(task, "sku_id"));
      } catch (RuntimeException ex) {
        log.warn("关单库存返还暂未完成，将继续重试", ex);
      }

    for (var order : orders.expiredOrders()) {
      String orderId = RowValues.string(order, "id");
      try {
        lifecycle.closeExpired(RowValues.longValue(order, "user_id"), orderId);
      } catch (RuntimeException ex) {
        log.warn("到期订单暂未关闭，orderId={}", orderId, ex);
      }
    }
    for (String id : traces.recoveryIds())
      try {
        recovery.reconcile(id);
      } catch (RuntimeException ex) {
        try {
          traces.checked(id, java.time.LocalDateTime.now().plusSeconds(30));
        } catch (RuntimeException ignored) {
          /* 数据库故障时下轮重试。 */
        }
        log.warn("下单任务恢复暂未完成，traceId={}", id);
      }
  }
}
