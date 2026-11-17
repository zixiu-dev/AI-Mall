package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.service.OrderSnapshotService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 可重试的库存整理：Redis 原子状态机和数据库进度一起使“执行后未记账”的重试安全。 */
@Service
public class InventorySettlementService {
  private final OrderTraceRepository traces;
  private final OrderInventoryMapper mapper;
  private final OrderSnapshotService snapshots;
  private final InventoryRedisStore redis;

  public InventorySettlementService(
      OrderTraceRepository traces,
      OrderInventoryMapper mapper,
      OrderSnapshotService snapshots,
      InventoryRedisStore redis) {
    this.traces = traces;
    this.mapper = mapper;
    this.snapshots = snapshots;
    this.redis = redis;
  }

  /** 订单已经提交后开启新事务，不占用原订单事务的连接等待整理。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void settleTrace(String id) {
    settleLocked(traces.lock(id));
  }

  /** 调用方已经持有 trace 锁，失败保持 *_PENDING，下一次按同一 Lua 标志继续。 */
  public void settleLocked(OrderTraceDO t) {
    String phase = t.getReserveStatus();
    String action;
    if ("CONFIRM_PENDING".equals(phase) && "SUCCESS".equals(t.getStatus())) action = "CONFIRM";
    else if ("RELEASE_PENDING".equals(phase)
        && java.util.Set.of("FAILED", "EXPIRED").contains(t.getStatus())) action = "RELEASE";
    else return;
    for (var item : snapshots.read(t.getRequestSnapshot()).getItems())
      redis.finish(item.getSkuId(), t.getTraceId(), item.getQuantity(), action);
    // Redis 与 SQL 不能同事务提交。先执行幂等 Lua，再记进度；中断后重复 Lua 安全。
    mapper.stage(
        Map.of(
            "id",
            t.getTraceId(),
            "expected",
            phase,
            "state",
            action.equals("CONFIRM") ? "CONFIRMED" : "RELEASED"));
  }

  /** 锁定一条关单返还任务；Lua 已成功但本地提交失败时，重试只补记进度。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void settleReturn(String order, long sku) {
    var args = Map.<String, Object>of("order", order, "sku", sku);
    var task = RowValues.required(mapper.lockReturn(args));
    if (RowValues.bool(task, "completed")) return;
    redis.finish(
        sku,
        RowValues.string(task, "reservation_id"),
        RowValues.intValue(task, "quantity"),
        "RETURN");
    mapper.completedReturn(args);
    mapper.returnCompleted(Map.of("order", order));
  }
}
