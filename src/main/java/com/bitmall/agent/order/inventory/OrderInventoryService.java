package com.bitmall.agent.order.inventory;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.service.OrderSnapshotService;
import com.bitmall.agent.order.vo.OrderDetailVO;
import java.util.*;
import org.springframework.stereotype.Service;

/** 同步、异步共用的交易库存入口；多 SKU 逐个同槽预占，部分失败由持久化意图补偿。 */
@Service
public class OrderInventoryService {
  private final InventoryReservationProperties properties;
  private final InventoryIntentService intents;
  private final InventoryPoolService pools;
  private final InventoryRedisStore redis;
  private final OrderTraceRepository traces;
  private final OrderSnapshotService snapshots;
  private final OrderInventoryMapper mapper;

  public OrderInventoryService(
      InventoryReservationProperties properties,
      InventoryIntentService intents,
      InventoryPoolService pools,
      InventoryRedisStore redis,
      OrderTraceRepository traces,
      OrderSnapshotService snapshots,
      OrderInventoryMapper mapper) {
    this.properties = properties;
    this.intents = intents;
    this.pools = pools;
    this.redis = redis;
    this.traces = traces;
    this.snapshots = snapshots;
    this.mapper = mapper;
  }

  /** 受理事务持有 trace 锁时再次核对预占，防止恢复任务已经释放后仍然入队。 */
  public void verifyPrepared(OrderTraceDO t) {
    requireReserved(t, snapshots.read(t.getRequestSnapshot()));
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /** 初始化通过另一个 Service 的事务代理完成；返回后才缓存已提交的初始化标记。 */
  private void ensure(long sku) {
    if (!pools.initialized(sku)) {
      pools.initialize(sku);
      pools.rememberInitialized(sku);
    }
  }

  /** 主事务之前完成 Lua 预占；重试使用同一个 trace，不会再扣。 */
  public String reserve(long user, String id) {
    if (!properties.isEnabled()) return traces.find(id).orElseThrow().getMode();
    // 先保存恢复凭据再操作 Redis，不能出现扣完库存却不知道该释放谁。
    String mode = intents.prepare(user, id);
    var t = traces.find(id).orElseThrow();
    if (t.getUserId() != user) throw new IllegalArgumentException("购买记录不属于当前用户");
    if (!"INIT".equals(t.getStatus())) return mode;
    // 按 SKU 排序处理。跨 SKU 不具备一次 Lua 的原子性，部分失败交给持久化补偿。
    for (var item :
        snapshots.read(t.getRequestSnapshot()).getItems().stream()
            .sorted(Comparator.comparingLong(CartItemVO::getSkuId))
            .toList()) {
      ensure(item.getSkuId());
      redis.reserve(item.getSkuId(), id, item.getQuantity());
    }
    return mode;
  }

  /** 建单消费者不能绕过预占；关闭 Redis 时也不能处理原来已经预占的任务。 */
  public void requireReserved(OrderTraceDO trace, CheckoutQuoteVO quote) {
    if ("NONE".equals(trace.getReserveStatus())) {
      if (properties.isEnabled()) throw new IllegalStateException("购买尚未执行库存预占");
      return;
    }
    for (var item : quote.getItems()) {
      pools.checkReady(item.getSkuId());
      redis.requireReserved(item.getSkuId(), trace.getTraceId(), item.getQuantity());
    }
  }

  /** 与 PROCESSING 受理同事务标记。 */
  public void admitted(String id) {
    if (properties.isEnabled() && mapper.admitted(Map.of("id", id)) != 1)
      throw new IllegalStateException("库存受理状态不匹配");
  }

  /** 关单前先确保旧订单的库存基线可用。旧订单只登记确认记录，绝不再次预扣。 */
  public void beforeClose(OrderDetailVO order) {
    if (!properties.isEnabled()) return;
    var row = RowValues.required(mapper.orderReservation(Map.of("order", order.getId())));
    String stage = RowValues.string(row, "reserve_status");
    boolean legacy = stage == null || "NONE".equals(stage);
    for (var item : order.getItems()) {
      ensure(item.getSkuId());
      if (legacy) redis.adopt(item.getSkuId(), "legacy:" + order.getId(), item.getQuantity());
    }
  }

  /** 与数据库关单、返库同事务写入返还任务，提交后才允许增加 Redis 可售。 */
  public void recordReturn(OrderDetailVO order) {
    if (!properties.isEnabled()) return;
    var row = RowValues.required(mapper.orderReservation(Map.of("order", order.getId())));
    String stage = RowValues.string(row, "reserve_status");
    String id =
        (stage == null || "NONE".equals(stage))
            ? "legacy:" + order.getId()
            : RowValues.string(row, "trace_id");
    for (var item : order.getItems())
      mapper.insertReturn(
          Map.of(
              "order",
              order.getId(),
              "sku",
              item.getSkuId(),
              "reservation",
              id,
              "quantity",
              item.getQuantity()));
    mapper.returnPending(Map.of("order", order.getId()));
  }
}
