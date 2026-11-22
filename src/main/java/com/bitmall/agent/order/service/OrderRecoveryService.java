package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderMessageRepository;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.inventory.InventorySettlementService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 恢复中断的购买：由定时扫描和建单死信消费调用。
 *
 * <p>先锁住 trace 行（与建单事务争同一把锁，所以已经成功的购买不会被覆盖成失败），确定该给它什么终态， 再整理 Redis 库存预占。库存整理由 Lua
 * 脚本保证幂等，重复执行不会重复归还。
 */
@Service
@RequiredArgsConstructor
public class OrderRecoveryService {
  private static final String STATUS_INIT = "INIT";
  private static final String STATUS_PROCESSING = "PROCESSING";
  private static final String MODE_ASYNC = "ASYNC";
  private static final String RESERVE_RESERVING = "RESERVING";

  /** 已发出但这么久还没被处理完的建单消息，视为丢失，重新投递。 */
  private static final long BUILD_REQUEUE_AFTER_SECONDS = 30;

  /** 排队中的任务下次检查的间隔。 */
  private static final long PROCESSING_RECHECK_SECONDS = 30;

  /** 库存整理失败后的重试间隔。 */
  private static final long SETTLEMENT_RETRY_SECONDS = 5;

  /** 锁定读取 trace，并记录下次检查时间。 */
  private final OrderTraceRepository traces;

  /** 把 trace 标记为终态失败（过期、受理中断、排队超时）。 */
  private final OrderTraceFailureService failures;

  /** 核对死信是否对应原建单消息；重投长时间未处理的建单消息。 */
  private final OrderMessageRepository messages;

  /** 按 trace 当前状态确认或释放 Redis 库存预占。 */
  private final InventorySettlementService inventory;

  /** 查询预占意图是否已超过受理宽限期仍未完成。 */
  private final OrderInventoryMapper inventoryMapper;

  /** 定时扫描入口：恢复一个到期需要检查的 trace。 */
  @Transactional
  public void reconcile(String id) {
    reconcileLocked(traces.lock(id));
  }

  /**
   * 建单消息进入死信后的恢复入口。
   *
   * @return false 表示这条死信不对应任何需要恢复的异步任务（trace 不存在、不是异步、还没受理，或消息与原建单消息不匹配）
   */
  @Transactional
  public boolean reconcileBuildDeadLetter(String traceId, String eventId) {
    OrderTraceDO trace = traces.lockIfPresent(traceId).orElse(null);
    if (trace == null) {
      return false;
    }
    boolean recoverable =
        MODE_ASYNC.equals(trace.getMode())
            && !STATUS_INIT.equals(trace.getStatus())
            && messages.matchesBuild(eventId, traceId);
    if (!recoverable) {
      return false;
    }
    reconcileLocked(trace);
    return true;
  }

  /** 调用方已持有 trace 行锁。 */
  private void reconcileLocked(OrderTraceDO trace) {
    String id = trace.getTraceId();
    LocalDateTime now = LocalDateTime.now();

    // 1. 还没受理（INIT）的购买：确认已过期，或预占后受理中断超过宽限期，都记为失败。
    if (STATUS_INIT.equals(trace.getStatus())) {
      boolean confirmationExpired = !trace.getExpiresAt().isAfter(now);
      if (confirmationExpired) {
        failures.fail(id, "EXPIRED", "确认已过期，请重新结算");
      } else if (RESERVE_RESERVING.equals(trace.getReserveStatus())
          && inventoryMapper.staleIntent(Map.of("id", id)) > 0) {
        failures.fail(id, "ADMISSION_INTERRUPTED", "库存预占后受理未完成，已安排释放，请重新购买");
      }
    }

    // 2. 排队中的异步任务：超过处理期限记为失败；否则重投可能丢失的建单消息，并安排下次检查。
    boolean queued =
        STATUS_PROCESSING.equals(trace.getStatus()) && MODE_ASYNC.equals(trace.getMode());
    if (queued) {
      recoverQueued(trace, LocalDateTime.now());
    }

    // 3. 按（可能刚更新的）终态整理 Redis 预占。
    try {
      inventory.settleLocked(traces.lock(id));
    } catch (RuntimeException settlementFailed) {
      // 吞掉异常，让上面记录的失败状态照常提交；库存整理保持 PENDING，几秒后再试。
      // 如果因为 Redis 故障让整个事务回滚，失败状态也会丢失，任务可能被重复接单。
      traces.checked(id, LocalDateTime.now().plusSeconds(SETTLEMENT_RETRY_SECONDS));
    }
  }

  private void recoverQueued(OrderTraceDO trace, LocalDateTime now) {
    String id = trace.getTraceId();
    LocalDateTime processingDeadline = trace.getProcessingExpiresAt();
    boolean timedOut = processingDeadline == null || !processingDeadline.isAfter(now);
    if (timedOut) {
      failures.fail(id, "QUEUE_TIMEOUT", "排队处理超时，本次未创建订单，请重新确认购买");
      return;
    }
    messages.requeueBuild(id, now.minusSeconds(BUILD_REQUEUE_AFTER_SECONDS));
    // 下次检查不晚于处理期限，保证超时后能及时记为失败。
    LocalDateTime nextCheck = now.plusSeconds(PROCESSING_RECHECK_SECONDS);
    if (nextCheck.isAfter(processingDeadline)) {
      nextCheck = processingDeadline;
    }
    traces.checked(id, nextCheck);
  }
}
