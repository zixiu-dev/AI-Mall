package com.bitmall.agent.order.service;

import com.bitmall.agent.common.idempotent.Idempotent;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.monitor.OrderMetrics;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 确认购买的统一提交入口（页面和 Agent 共用）。
 *
 * <p>重复提交的防护分三层：{@link Idempotent} 切面用分布式锁挡住同一购买编号的并发请求；建单事务里的状态 CAS 保证只有一个请求能真正受理；订单表上 trace
 * 的唯一键做最后兜底。重复请求不会报错，而是返回已有的购买进度。
 */
@Service
@RequiredArgsConstructor
public class OrderSubmissionService {
  private static final String MODE_SYNC = "SYNC";
  private static final String MODE_ASYNC = "ASYNC";

  /** 校验 trace 属于当前用户，并在最后查询购买进度作为返回结果。 */
  private final OrderTraceService traces;

  /** 同步模式下直接在事务里建单。 */
  private final OrderCreationService creation;

  /** 异步模式下只做受理：持久化受理状态和待发送消息，由 MQ 消费者稍后建单。 */
  private final OrderAsyncAdmissionService asyncAdmission;

  /** 建单被业务规则拒绝（库存不足、已过期等）后，单独记录 trace 失败状态和原因。 */
  private final OrderTraceFailureService failures;

  /** 控制单实例下单并发数（超过上限直接拒绝），并记录各模式的处理耗时。 */
  private final OrderMetrics metrics;

  /** 预占 Redis 库存，并决定本次购买走同步还是异步（AUTO 模式下在首次预占时固定）。 */
  private final OrderInventoryService inventory;

  /** 首次提交时创建 trace 并绑定购买内容。 */
  private final OrderTraceBindingService binding;

  /** 直接查询 trace 是否已存在，以及读取已绑定的提交摘要。 */
  private final OrderTraceRepository records;

  /** 计算购买内容的摘要，核对重试请求与首次提交的内容一致。 */
  private final OrderSnapshotService snapshots;

  /** 旧入口：不带购买内容，trace 必须已经由结算流程创建好。重试复用已有 trace，不会重新发号。 */
  @Idempotent(scene = "ORDER_SUBMIT", key = "#user + ':' + #id")
  public OrderSubmitVO submit(long user, String id) {
    return submit(user, id, null);
  }

  /**
   * 提交一次购买。首次提交会把购买内容和 trace 绑定；重试时只核对内容摘要是否一致， 不会重新试算价格（此时券可能已被本次购买锁定，重新试算反而会失败）。
   *
   * <p>被业务规则拒绝时不抛出，而是把 trace 记为失败后返回购买进度；但如果当前处在外层事务里（Agent 确认执行）， 则直接抛出，让外层事务先回滚，由外层调用方记录失败。
   */
  @Idempotent(scene = "ORDER_SUBMIT", key = "#user + ':' + #id")
  public OrderSubmitVO submit(long user, String id, CreateOrderCommand command) {
    // 先占并发名额再读数据库：服务繁忙时直接拒绝，不让请求继续压到数据库上。
    long started = metrics.enter();
    String mode = MODE_SYNC;
    try {
      try {
        OrderTraceDO trace = bindOnFirstSubmission(user, id, command);
        traces.owned(trace, user);
        requireSameCommand(trace, command);
        // 再查一次归属；执行模式以服务端记录为准，不信任客户端。
        traces.get(user, id);
        mode = inventory.reserve(user, id);
        if (MODE_ASYNC.equals(mode)) {
          asyncAdmission.admit(user, id);
        } else {
          creation.create(user, id);
        }
      } catch (OrderRejectedException rejected) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
          throw rejected;
        }
        // trace 都还没建成，没有可以记失败的记录。
        if (records.find(id).isEmpty()) {
          throw rejected;
        }
        failures.fail(id, rejected.getCode(), rejected.getMessage());
      }
      return traces.get(user, id);
    } finally {
      metrics.exit(started, mode);
    }
  }

  /** trace 不存在时用本次购买内容创建它，然后返回（新建的或已有的）trace。 */
  private OrderTraceDO bindOnFirstSubmission(long user, String id, CreateOrderCommand command) {
    if (records.find(id).isEmpty()) {
      if (command == null) {
        throw new IllegalArgumentException("首次提交必须携带购买内容");
      }
      try {
        binding.bind(user, id, command);
      } catch (DuplicateKeyException concurrentBind) {
        // 并发请求抢先建好了 trace；下面重新读取后照常核对归属和内容。
      }
    }
    return records.find(id).orElseThrow();
  }

  /** 同一购买编号重试时，商品、数量和结算参数必须与首次提交一致。 */
  private void requireSameCommand(OrderTraceDO trace, CreateOrderCommand command) {
    if (command == null || trace.getSubmissionHash() == null) {
      return;
    }
    if (!snapshots.commandHash(command).equals(trace.getSubmissionHash())) {
      throw new IllegalArgumentException("同一购买编号不能更改商品、数量或结算参数，请重新确认");
    }
  }
}
