package com.bitmall.agent.order.service;

import com.bitmall.agent.common.idempotent.Idempotent;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.inventory.OrderInventoryService;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.monitor.OrderMetrics;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 统一确认提交入口：AOP 分布式锁挡并发，事务服务的 CAS 和订单唯一键兜底，重复请求返回已有任务。 */
@Service
public class OrderSubmissionService {
  /** 后端发号与归属检查。 */
  private final OrderTraceService traces;

  /** 受理事务服务，避免同类调用绕过代理。 */
  private final OrderCreationService creation;

  /** 异步受理只持久化任务与命令。 */
  private final OrderAsyncAdmissionService asyncAdmission;

  /** 交易回滚后记录失败。 */
  private final OrderTraceFailureService failures;

  /** 监控与入口并发保护。 */
  private final OrderMetrics metrics;

  private final OrderInventoryService inventory;
  private final OrderTraceBindingService binding;
  private final OrderTraceRepository records;
  private final OrderSnapshotService snapshots;

  /** 注入统一受理链路。 */
  public OrderSubmissionService(
      OrderTraceService traces,
      OrderCreationService creation,
      OrderAsyncAdmissionService asyncAdmission,
      OrderTraceFailureService failures,
      OrderMetrics metrics,
      OrderInventoryService inventory,
      OrderTraceBindingService binding,
      OrderTraceRepository records,
      OrderSnapshotService snapshots) {
    this.traces = traces;
    this.creation = creation;
    this.asyncAdmission = asyncAdmission;
    this.failures = failures;
    this.metrics = metrics;
    this.inventory = inventory;
    this.binding = binding;
    this.records = records;
    this.snapshots = snapshots;
  }

  /** 所有重试复用后端已有 trace；不是每次 HTTP 调用都重新发号。 */
  @Idempotent(scene = "ORDER_SUBMIT", key = "#user + ':' + #id")
  public OrderSubmitVO submit(long user, String id) {
    return submit(user, id, null);
  }

  /** 新页面提交参数与 trace 一起绑定；已有购买重试只检查摘要，不因券已锁定而重新试算。 */
  @Idempotent(scene = "ORDER_SUBMIT", key = "#user + ':' + #id")
  public OrderSubmitVO submit(long user, String id, CreateOrderCommand command) {
    // 并发保护先于数据库读取，服务繁忙时不继续穿透数据库。
    long started = metrics.enter();
    String mode = "SYNC";
    try {
      try {
        var existing = records.find(id);
        if (existing.isEmpty()) {
          if (command == null) throw new IllegalArgumentException("首次提交必须携带购买内容");
          try {
            binding.bind(user, id, command);
          } catch (DuplicateKeyException concurrent) {
            /* 唯一键竞争后重新核对归属和内容。 */
          }
        }
        var bound = records.find(id).orElseThrow();
        traces.owned(bound, user);
        if (command != null
            && bound.getSubmissionHash() != null
            && !snapshots.commandHash(command).equals(bound.getSubmissionHash()))
          throw new IllegalArgumentException("同一购买编号不能更改商品、数量或结算参数，请重新确认");
        var trace = traces.get(user, id); // 先检查归属，不信任客户端指定执行模式。
        mode = inventory.reserve(user, id); // AUTO 在首次预占意图事务里固定本次模式。
        if ("ASYNC".equals(mode)) asyncAdmission.admit(user, id);
        else creation.create(user, id);
      } catch (OrderRejectedException ex) {
        // 外层 Agent 事务仍活动时，必须先让它回滚；其调用方随后记录失败。
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw ex;
        if (records.find(id).isEmpty()) throw ex;
        failures.fail(id, ex.getCode(), ex.getMessage());
      }
      return traces.get(user, id);
    } finally {
      metrics.exit(started, mode);
    }
  }
}
