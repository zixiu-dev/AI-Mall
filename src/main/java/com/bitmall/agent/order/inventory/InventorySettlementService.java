package com.bitmall.agent.order.inventory;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.service.OrderSnapshotService;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 整理 Redis 库存预占：订单成功后确认扣减，购买失败后释放，订单关闭后归还。
 *
 * <p>Redis 和数据库无法在同一个事务里提交，所以固定顺序为"先执行 Redis Lua，再在数据库里记录进度"。 Lua 脚本按预占记录的状态做幂等判断， 如果 Lua
 * 已执行但数据库进度没记上（例如中途宕机），重试时 Lua 不会重复扣减或归还，只会把进度补记上。 整理失败时进度保持 *_PENDING，等下一次重试。
 */
@Service
@RequiredArgsConstructor
public class InventorySettlementService {
  private static final String RESERVE_CONFIRM_PENDING = "CONFIRM_PENDING";
  private static final String RESERVE_RELEASE_PENDING = "RELEASE_PENDING";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final Set<String> FAILED_STATUSES = Set.of("FAILED", "EXPIRED");

  private static final String ACTION_CONFIRM = "CONFIRM";
  private static final String ACTION_RELEASE = "RELEASE";
  private static final String ACTION_RETURN = "RETURN";

  /** 锁定读取 trace，取得它的购买状态和预占整理进度。 */
  private final OrderTraceRepository traces;

  /** 记录 trace 的预占整理进度，以及锁定和完成关单归还任务。 */
  private final OrderInventoryMapper mapper;

  /** 从 trace 保存的购买快照里读出每个 SKU 的预占数量。 */
  private final OrderSnapshotService snapshots;

  /** 执行 Redis 库存整理的 Lua 脚本（确认、释放、归还）。 */
  private final InventoryRedisStore redis;

  /** 订单事务提交后调用；开新事务，不占着原订单事务的连接等 Redis。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void settleTrace(String id) {
    settleLocked(traces.lock(id));
  }

  /**
   * 按 trace 当前状态整理预占。调用方必须已持有 trace 行锁。
   *
   * <ul>
   *   <li>CONFIRM_PENDING 且购买成功 → 确认扣减，进度记为 CONFIRMED；
   *   <li>RELEASE_PENDING 且购买失败/过期 → 释放预占，进度记为 RELEASED；
   *   <li>其他情况无需整理，直接返回。
   * </ul>
   */
  public void settleLocked(OrderTraceDO trace) {
    String phase = trace.getReserveStatus();
    String action = settlementAction(phase, trace.getStatus());
    if (action == null) {
      return;
    }
    for (CartItemVO item : snapshots.read(trace.getRequestSnapshot()).getItems()) {
      redis.finish(item.getSkuId(), trace.getTraceId(), item.getQuantity(), action);
    }
    // 只有进度仍是 phase 时才更新（条件更新），防止并发整理互相覆盖。
    String finishedState = ACTION_CONFIRM.equals(action) ? "CONFIRMED" : "RELEASED";
    mapper.stage(Map.of("id", trace.getTraceId(), "expected", phase, "state", finishedState));
  }

  /** 返回要执行的整理动作；不需要整理时返回 null。 */
  private static String settlementAction(String phase, String status) {
    if (RESERVE_CONFIRM_PENDING.equals(phase) && STATUS_SUCCESS.equals(status)) {
      return ACTION_CONFIRM;
    }
    if (RESERVE_RELEASE_PENDING.equals(phase) && FAILED_STATUSES.contains(status)) {
      return ACTION_RELEASE;
    }
    return null;
  }

  /**
   * 执行一条关单归还任务（一个订单的一个 SKU）：把预占的数量归还到 Redis 可售量。
   *
   * <p>任务行加锁读取，已完成就直接返回。全部 SKU 都归还完后，trace 的进度改为 RETURNED。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void settleReturn(String order, long sku) {
    Map<String, Object> args = Map.of("order", order, "sku", sku);
    Map<String, Object> task = RowValues.required(mapper.lockReturn(args));
    if (RowValues.bool(task, "completed")) {
      return;
    }
    redis.finish(
        sku,
        RowValues.string(task, "reservation_id"),
        RowValues.intValue(task, "quantity"),
        ACTION_RETURN);
    mapper.completedReturn(args);
    mapper.returnCompleted(Map.of("order", order));
  }
}
