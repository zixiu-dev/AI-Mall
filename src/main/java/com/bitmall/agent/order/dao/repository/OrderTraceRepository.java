package com.bitmall.agent.order.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderTraceMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 下单 trace 的 SQL。状态和版本条件、行锁、订单唯一键共同保护重复受理和重复建单。 */
@Repository
public class OrderTraceRepository {
  /** 所有状态与订单使用同一数据源。 */
  private final OrderTraceMapper mapper;

  /** Mapper XML 显式控制行锁与条件更新。 */

  /** 注入数据库访问工具。 */
  public OrderTraceRepository(OrderTraceMapper mapper) {
    this.mapper = mapper;
  }

  /** 保存购买内容。旧内部入口可不提供参数摘要，新页面首次提交会提供。 */
  public void insert(
      String id,
      long user,
      String origin,
      String mode,
      String snapshot,
      String hash,
      LocalDateTime expires) {
    insert(id, user, origin, mode, snapshot, hash, expires, null);
  }

  public void insert(
      String id,
      long user,
      String origin,
      String mode,
      String snapshot,
      String hash,
      LocalDateTime expires,
      String submissionHash) {
    mapper.insertSql(
        parameters(
            "id",
            id,
            "u",
            user,
            "origin",
            origin,
            "mode",
            mode,
            "snapshot",
            snapshot,
            "hash",
            hash,
            "expires",
            expires,
            "submissionHash",
            submissionHash));
  }

  public Optional<OrderTraceDO> find(String id) {
    return mapper.findSql(parameters("id", id)).stream().findFirst().map(this::row);
  }

  /** 锁定读取必须用于建单、失败和恢复的状态判断。 */
  public OrderTraceDO lock(String id) {
    return lockIfPresent(id).orElseThrow(() -> new IllegalArgumentException("下单任务不存在"));
  }

  /** 死信核对时允许记录缺失，数据库查询异常仍向调用方传播。 */
  public Optional<OrderTraceDO> lockIfPresent(String id) {
    return mapper.lockSql(parameters("id", id)).stream()
        .findFirst()
        .map(this::row);
  }

  public Optional<OrderTraceDO> findOrigin(String origin) {
    return mapper.findOriginSql(parameters("origin", origin)).stream().findFirst().map(this::row);
  }

  private LocalDateTime time(Map<String, Object> row, String name) {
    var value = RowValues.timestamp(row, name);
    return value == null ? null : value.toLocalDateTime();
  }

  private OrderTraceDO row(Map<String, Object> row) {
    return new OrderTraceDO(
        RowValues.string(row, "trace_id"),
        RowValues.longValue(row, "user_id"),
        RowValues.string(row, "origin_key"),
        RowValues.string(row, "mode"),
        RowValues.string(row, "request_snapshot"),
        RowValues.string(row, "request_hash"),
        RowValues.string(row, "status"),
        RowValues.string(row, "order_id"),
        RowValues.string(row, "result_json"),
        RowValues.string(row, "failure_code"),
        RowValues.string(row, "failure_reason"),
        RowValues.intValue(row, "version"),
        time(row, "expires_at"),
        time(row, "created_at"),
        time(row, "accepted_at"),
        time(row, "processing_expires_at"),
        RowValues.string(row, "reserve_status"),
        RowValues.string(row, "submission_hash"));
  }

  /** CAS 抢占受理权；同一 INIT 版本只能变更一次。 */
  public boolean claim(OrderTraceDO trace) {
    return claim(trace, null);
  }

  /** 异步受理持久化独立处理期限，重启或配置改变不会让已经排队的任务无限等待。 */
  public boolean claim(OrderTraceDO trace, LocalDateTime deadline) {
    return mapper.claimSql(
            parameters(
                "id", trace.getTraceId(), "version", trace.getVersion(), "deadline", deadline))
        == 1;
  }

  /** 与订单创建一起 CAS 标记成功，状态冲突时抛异常回滚订单。 */
  public void succeed(OrderTraceDO trace, String orderId, String result) {
    int n =
        mapper.succeedSql(
            parameters(
                "id",
                trace.getTraceId(),
                "version",
                trace.getVersion(),
                "order",
                orderId,
                "result",
                result,
                "reserve",
                "NONE".equals(trace.getReserveStatus()) ? "NONE" : "CONFIRM_PENDING"));
    if (n != 1) throw new IllegalStateException("下单状态已变化，本次业务事务回滚");
  }

  /** 在事务已确认不能继续建单后记录终态失败；库存和券已由业务事务回滚。 */
  public void fail(OrderTraceDO trace, String code, String reason) {
    mapper.failSql(
        parameters(
            "id",
            trace.getTraceId(),
            "version",
            trace.getVersion(),
            "status",
            "EXPIRED".equals(code) ? "EXPIRED" : "FAILED",
            "code",
            code,
            "reason",
            reason.substring(0, Math.min(500, reason.length())),
            "reserve",
            "NONE".equals(trace.getReserveStatus()) ? "NONE" : "RELEASE_PENDING"));
  }

  /** 按轮询时间分批扫描过期凭证；不会让没有预占的旧 INIT 永久占满扫描批次。 */
  public List<String> recoveryIds() {
    return mapper.recoveryIdsSql(java.util.Map.of());
  }

  /** 每次检查后延迟下一次扫描，实例重启后仍保留调度进度。 */
  public void checked(String id, LocalDateTime next) {
    mapper.checkedSql(parameters("id", id, "next", next));
  }

  /** 定期采样业务积压，不能在每一次请求里聚合全表。 */
  public Map<String, Object> backlog() {
    return RowValues.required(mapper.backlogSql(java.util.Map.of()));
  }

  /** 待建单与待确认、待释放分别计量，供后台监控采样。 */
  public List<Map<String, Object>> asyncBuildBacklog() {
    return mapper.asyncBuildBacklogSql();
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
