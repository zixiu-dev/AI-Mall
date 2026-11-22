package com.bitmall.agent.order.inventory;

import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.service.OrderSubmissionPolicy;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在执行 Redis 预占之前，先在独立事务里记下"准备预占"的意图（reserve_status = RESERVING）。
 *
 * <p>之所以要单独提交：Redis 预占可能只成功了一部分，随后的受理事务又回滚了。 有了这条已提交的意图记录，恢复任务就能知道这笔购买可能留有预占， 并按购买快照把它们释放掉。
 */
@Service
@RequiredArgsConstructor
public class InventoryIntentService {
  private static final String STATUS_INIT = "INIT";
  private static final String RESERVE_NONE = "NONE";
  private static final String MODE_ASYNC = "ASYNC";

  /** 锁定读取 trace，检查归属、状态和当前预占进度。 */
  private final OrderTraceRepository traces;

  /** 写入本次购买的执行模式和预占意图。 */
  private final OrderInventoryMapper mapper;

  /** 读取受理宽限期：意图登记后超过这个时间仍未受理，恢复任务会接手。 */
  private final InventoryReservationProperties properties;

  /** 首次预占时决定执行模式（AUTO 模式按当前压力选择），异步模式下检查队列可用。 */
  private final OrderSubmissionPolicy policy;

  /**
   * 准备预占并返回本次购买的执行模式（SYNC / ASYNC）。
   *
   * <p>首次预占时选定模式并写入 trace，之后的重试都沿用这个模式，不会中途切换。 trace 已不在 INIT 状态（已受理或已结束）时直接返回它记录的模式。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String prepare(long user, String id) {
    OrderTraceDO trace = traces.lock(id);
    if (trace.getUserId() != user) {
      throw new IllegalArgumentException("购买记录不属于当前用户");
    }
    if (!STATUS_INIT.equals(trace.getStatus())) {
      return trace.getMode();
    }
    boolean confirmationExpired = !trace.getExpiresAt().isAfter(LocalDateTime.now());
    if (confirmationExpired) {
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    }
    boolean firstReservation = RESERVE_NONE.equals(trace.getReserveStatus());
    String mode = firstReservation ? policy.firstSubmissionMode(trace.getMode()) : trace.getMode();
    // 仍在 INIT 的重试也要检查：Broker 故障期间不能继续推进未完成的预占。
    if (MODE_ASYNC.equals(mode)) {
      policy.requireQueueAvailable();
    }
    if (firstReservation) {
      mapper.route(Map.of("id", id, "mode", mode));
      LocalDateTime recoveryCheckAt =
          LocalDateTime.now().plusSeconds(properties.getAdmissionGraceSeconds());
      mapper.intent(Map.of("id", id, "next", recoveryCheckAt));
    }
    return mode;
  }
}
