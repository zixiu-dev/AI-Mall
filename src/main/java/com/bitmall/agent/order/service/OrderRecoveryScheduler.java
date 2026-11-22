package com.bitmall.agent.order.service;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/** 定期关闭到期待支付订单并回收过期购买凭证。通过另一个 Service 的代理开启每条任务的事务。 */
@Component
@RequiredArgsConstructor
public class OrderRecoveryScheduler {
  /** 查询到期需要检查的 trace，并推迟下次检查时间。 */
  private final OrderTraceRepository traces;

  /** 恢复一个中断的购买。 */
  private final OrderRecoveryService recovery;

  /** 查询已超过支付期限但仍待支付的订单。 */
  private final OrderRepository orders;

  /** 关闭已超时未支付的订单（兜底，防止延迟消息丢失）。 */
  private final OrderLifecycleService lifecycle;

  /** 读取待执行的关单库存归还任务。 */
  private final OrderInventoryMapper inventoryMapper;

  /** 在数据库事务提交后确认预占，失败或关单后幂等释放库存。 */
  private final InventorySettlementService inventory;

  /** 失败只记录编号，下一轮继续。 */
  private static final Logger log = LoggerFactory.getLogger(OrderRecoveryScheduler.class);

  /** 后台恢复不删除失败任务，也不把超时直接等同于订单失败。 */
  @Scheduled(fixedDelayString = "${app.order-processing.recovery-interval-ms:5000}")
  public void recover() {
    for (Map<String, Object> task : inventoryMapper.pendingReturns()) {
      try {
        inventory.settleReturn(
            RowValues.string(task, "order_id"), RowValues.longValue(task, "sku_id"));
      } catch (RuntimeException ex) {
        log.warn("关单库存返还暂未完成，将继续重试", ex);
      }
    }

    for (Map<String, Object> order : orders.expiredOrders()) {
      String orderId = RowValues.string(order, "id");
      try {
        lifecycle.closeExpired(RowValues.longValue(order, "user_id"), orderId);
      } catch (RuntimeException ex) {
        log.warn("到期订单暂未关闭，orderId={}", orderId, ex);
      }
    }
    for (String id : traces.recoveryIds()) {
      try {
        recovery.reconcile(id);
      } catch (RuntimeException ex) {
        try {
          traces.checked(id, LocalDateTime.now().plusSeconds(30));
        } catch (RuntimeException ignored) {
          /* 数据库故障时下轮重试。 */
        }
        log.warn("下单任务恢复暂未完成，traceId={}", id);
      }
    }
  }
}
