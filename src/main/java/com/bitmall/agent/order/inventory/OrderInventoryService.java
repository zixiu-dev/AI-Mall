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
import com.bitmall.agent.order.vo.OrderItemVO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 同步、异步下单共用的交易库存入口（Redis 预占 + 数据库库存意图）。
 *
 * <p>每个 SKU 的预占在 Redis 中是一次原子 Lua 调用，但多个 SKU 之间不是原子的：可能出现前几个 SKU 已预占、后面失败的情况。
 * 因此预占前先持久化库存意图，部分成功的预占由后台恢复任务按意图补偿释放。
 *
 * <p>库存预扣开关关闭（properties.isEnabled() 为 false）时，本类大部分方法直接跳过，只依赖数据库条件扣库存。
 */
@Service
@RequiredArgsConstructor
public class OrderInventoryService {
  private static final String TRACE_STATUS_INIT = "INIT";

  /** trace 没有做过 Redis 预占。 */
  private static final String RESERVE_NONE = "NONE";

  /** 预扣上线前创建的旧订单没有预占记录，用 "legacy:订单号" 作为其在 Redis 中的登记编号。 */
  private static final String LEGACY_RESERVATION_PREFIX = "legacy:";

  /** 读取是否启用 Redis 库存预占；未启用时预占相关步骤全部跳过。 */
  private final InventoryReservationProperties properties;

  /** 持久化购买库存意图，支持 Redis 预占部分完成后的恢复。 */
  private final InventoryIntentService intents;

  /** 建立交易可售库存池，并结合数据库与预占状态校准库存。 */
  private final InventoryPoolService pools;

  /** 用交易 Redis Lua 执行原子库存预占、确认和幂等释放。 */
  private final InventoryRedisStore redis;

  /** 读取 trace 的执行模式和归属；未启用预占时直接返回已记录的模式。 */
  private final OrderTraceRepository traces;

  /** 从购买快照读出每个 SKU 的数量，按它预占库存。 */
  private final OrderSnapshotService snapshots;

  /** 读写库存预占意图和关单归还任务。 */
  private final OrderInventoryMapper mapper;

  /** 异步受理事务在持有 trace 行锁时再次核对预占仍然有效，防止恢复任务已经释放预占后任务仍被放入队列。 */
  public void verifyPrepared(OrderTraceDO trace) {
    requireReserved(trace, snapshots.read(trace.getRequestSnapshot()));
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * 确保 SKU 的 Redis 库存池已初始化。
   *
   * <p>初始化通过 InventoryPoolService 的事务代理完成；只有 initialize() 返回（事务已提交）后才在本地缓存"已初始化"标记，
   * 避免缓存一个最终回滚了的初始化结果。
   */
  private void ensurePoolInitialized(long sku) {
    if (!pools.initialized(sku)) {
      pools.initialize(sku);
      pools.rememberInitialized(sku);
    }
  }

  /**
   * 在建单主事务开始之前，按购买快照在 Redis 中预占库存，返回本次购买的执行模式。
   *
   * <p>重试使用同一个 trace 编号，Lua 脚本按编号幂等处理，不会重复扣减。trace 已离开 INIT 状态（已受理或已结束）时不再预占。
   */
  public String reserve(long user, String id) {
    if (!properties.isEnabled()) {
      return traces.find(id).orElseThrow().getMode();
    }
    // 必须先持久化恢复凭据再操作 Redis：否则可能扣了 Redis 库存却没有记录可以据此释放。
    String mode = intents.prepare(user, id);
    OrderTraceDO trace = traces.find(id).orElseThrow();
    if (trace.getUserId() != user) {
      throw new IllegalArgumentException("购买记录不属于当前用户");
    }
    if (!TRACE_STATUS_INIT.equals(trace.getStatus())) {
      return mode;
    }
    // 按 SKU 编号顺序预占；跨 SKU 不是一次原子 Lua，部分失败由持久化的意图补偿。
    List<CartItemVO> itemsInSkuOrder =
        snapshots.read(trace.getRequestSnapshot()).getItems().stream()
            .sorted(Comparator.comparingLong(CartItemVO::getSkuId))
            .toList();
    for (CartItemVO item : itemsInSkuOrder) {
      ensurePoolInitialized(item.getSkuId());
      redis.reserve(item.getSkuId(), id, item.getQuantity());
    }
    return mode;
  }

  /**
   * 建单前核对每个 SKU 都有数量匹配的有效预占，建单（包括 MQ 消费者）不能绕过预占。
   *
   * <p>trace 没有预占记录（NONE）时：预扣开启则拒绝建单；预扣关闭则放行，走纯数据库扣库存。反过来，已经做过预占的任务即使后来
   * 关闭了预扣，也必须核对预占，不能当成普通任务处理。
   */
  public void requireReserved(OrderTraceDO trace, CheckoutQuoteVO quote) {
    if (RESERVE_NONE.equals(trace.getReserveStatus())) {
      if (properties.isEnabled()) {
        throw new IllegalStateException("购买尚未执行库存预占");
      }
      return;
    }
    for (CartItemVO item : quote.getItems()) {
      pools.checkReady(item.getSkuId());
      redis.requireReserved(item.getSkuId(), trace.getTraceId(), item.getQuantity());
    }
  }

  /** 与 trace 进入 PROCESSING 的受理更新在同一事务中标记库存意图为已受理；状态不匹配时抛异常回滚受理。 */
  public void admitted(String id) {
    if (!properties.isEnabled()) {
      return;
    }
    int changed = mapper.admitted(Map.of("id", id));
    if (changed != 1) {
      throw new IllegalStateException("库存受理状态不匹配");
    }
  }

  /**
   * 关单前确保订单涉及的 SKU 库存池可用。
   *
   * <p>预扣上线前的旧订单在 Redis 中没有预占记录，这里只用 adopt 登记一条已确认记录，供随后的返还流程使用；绝不会再次预扣。
   */
  public void beforeClose(OrderDetailVO order) {
    if (!properties.isEnabled()) {
      return;
    }
    boolean legacyOrder = isLegacyOrder(loadOrderReservation(order));
    for (OrderItemVO item : order.getItems()) {
      ensurePoolInitialized(item.getSkuId());
      if (legacyOrder) {
        redis.adopt(item.getSkuId(), legacyReservationId(order), item.getQuantity());
      }
    }
  }

  /**
   * 与数据库关单、返还库存在同一事务中写入 Redis 可售返还任务。
   *
   * <p>这里只落库任务，事务提交后才由后台任务增加 Redis 可售，避免关单回滚了却已经把可售加回去。
   */
  public void recordReturn(OrderDetailVO order) {
    if (!properties.isEnabled()) {
      return;
    }
    Map<String, Object> reservation = loadOrderReservation(order);
    String reservationId =
        isLegacyOrder(reservation)
            ? legacyReservationId(order)
            : RowValues.string(reservation, "trace_id");
    for (OrderItemVO item : order.getItems()) {
      mapper.insertReturn(
          Map.of(
              "order",
              order.getId(),
              "sku",
              item.getSkuId(),
              "reservation",
              reservationId,
              "quantity",
              item.getQuantity()));
    }
    mapper.returnPending(Map.of("order", order.getId()));
  }

  /** 读取订单对应的预占记录（trace 编号与预占状态），必须恰好一行。 */
  private Map<String, Object> loadOrderReservation(OrderDetailVO order) {
    return RowValues.required(mapper.orderReservation(Map.of("order", order.getId())));
  }

  /** 没有预占状态或预占状态为 NONE 的订单，是预扣上线前创建、未经 Redis 预占的旧订单。 */
  private static boolean isLegacyOrder(Map<String, Object> reservation) {
    String reserveStatus = RowValues.string(reservation, "reserve_status");
    return reserveStatus == null || RESERVE_NONE.equals(reserveStatus);
  }

  private static String legacyReservationId(OrderDetailVO order) {
    return LEGACY_RESERVATION_PREFIX + order.getId();
  }
}
