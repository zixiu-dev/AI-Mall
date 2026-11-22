package com.bitmall.agent.order.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.mapper.OrderTraceMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 下单 trace（一次购买请求的受理记录）的数据访问。
 *
 * <p>防重复受理、重复建单靠三层数据库保护：状态 + 版本号的条件更新（CAS）、Mapper XML 中显式的行锁读取、以及订单表上
 * trace 的唯一键。
 */
@Repository
@RequiredArgsConstructor
public class OrderTraceRepository {
  /** trace 未做 Redis 库存预占（库存预扣关闭时创建的记录）。 */
  private static final String RESERVE_NONE = "NONE";

  /** 订单已提交，等待把 Redis 预占确认为实际扣减。 */
  private static final String RESERVE_CONFIRM_PENDING = "CONFIRM_PENDING";

  /** 建单失败，等待释放 Redis 预占。 */
  private static final String RESERVE_RELEASE_PENDING = "RELEASE_PENDING";

  private static final String STATUS_FAILED = "FAILED";
  private static final String STATUS_EXPIRED = "EXPIRED";

  /** 失败原因列的长度上限，超出部分截断。 */
  private static final int FAILURE_REASON_MAX_LENGTH = 500;

  /** 购买 trace 的 SQL，定义在 mapper/order/OrderTraceMapper.xml。 */
  private final OrderTraceMapper mapper;

  /** 保存购买内容，不带提交参数摘要。供旧的内部入口使用；新页面首次提交走带 submissionHash 的重载。 */
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

  /** 保存购买内容；submissionHash 是首次提交参数的摘要，可为 null。 */
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

  /** 普通读取，不加锁；不能用于决定状态转换。 */
  public Optional<OrderTraceDO> find(String id) {
    return mapper.findSql(parameters("id", id)).stream().findFirst().map(this::toTrace);
  }

  /** 加行锁读取，记录不存在时抛异常。建单、失败、恢复的状态判断都必须基于这次锁定读取。 */
  public OrderTraceDO lock(String id) {
    return lockIfPresent(id).orElseThrow(() -> new IllegalArgumentException("下单任务不存在"));
  }

  /** 加行锁读取，记录不存在时返回空（死信核对时允许缺失）；数据库查询异常仍向调用方传播。 */
  public Optional<OrderTraceDO> lockIfPresent(String id) {
    return mapper.lockSql(parameters("id", id)).stream().findFirst().map(this::toTrace);
  }

  /** 按来源键（同一来源请求的幂等键）查找 trace。 */
  public Optional<OrderTraceDO> findOrigin(String origin) {
    return mapper.findOriginSql(parameters("origin", origin)).stream()
        .findFirst()
        .map(this::toTrace);
  }

  private LocalDateTime time(Map<String, Object> row, String name) {
    Timestamp value = RowValues.timestamp(row, name);
    return value == null ? null : value.toLocalDateTime();
  }

  private OrderTraceDO toTrace(Map<String, Object> row) {
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

  /** 同步受理：用版本号 CAS 抢占受理权，同一个 INIT 版本只有一个请求能成功。 */
  public boolean claim(OrderTraceDO trace) {
    return claim(trace, null);
  }

  /**
   * 用版本号 CAS 抢占受理权。异步受理会传入处理期限并持久化，这样即使实例重启或配置改变，已排队的任务也不会无限等待。
   */
  public boolean claim(OrderTraceDO trace, LocalDateTime deadline) {
    int changed =
        mapper.claimSql(
            parameters(
                "id", trace.getTraceId(), "version", trace.getVersion(), "deadline", deadline));
    return changed == 1;
  }

  /**
   * 与订单创建在同一事务中，用版本号 CAS 把 trace 标记为成功。
   *
   * <p>状态已被其他线程改变时抛异常，让整个建单事务（含订单、库存、券）回滚。有 Redis 预占的 trace 同时进入"待确认预占"。
   */
  public void succeed(OrderTraceDO trace, String orderId, String result) {
    String reserveStatus = nextReserveStatus(trace, RESERVE_CONFIRM_PENDING);
    int changed =
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
                reserveStatus));
    if (changed != 1) {
      throw new IllegalStateException("下单状态已变化，本次业务事务回滚");
    }
  }

  /**
   * 记录终态失败：失败码为 EXPIRED 时状态记为 EXPIRED，否则记为 FAILED。调用时业务事务已确认不能继续建单，库存和券已回滚。
   *
   * <p>有 Redis 预占的 trace 同时进入"待释放预占"，由后台任务归还可售库存。失败原因超过 500 字符时截断。
   */
  public void fail(OrderTraceDO trace, String code, String reason) {
    String status = STATUS_EXPIRED.equals(code) ? STATUS_EXPIRED : STATUS_FAILED;
    String truncatedReason = reason.substring(0, Math.min(FAILURE_REASON_MAX_LENGTH, reason.length()));
    String reserveStatus = nextReserveStatus(trace, RESERVE_RELEASE_PENDING);
    mapper.failSql(
        parameters(
            "id",
            trace.getTraceId(),
            "version",
            trace.getVersion(),
            "status",
            status,
            "code",
            code,
            "reason",
            truncatedReason,
            "reserve",
            reserveStatus));
  }

  /** 没有 Redis 预占的 trace 保持 NONE；有预占的进入给定的待整理状态。 */
  private static String nextReserveStatus(OrderTraceDO trace, String pendingStatus) {
    if (RESERVE_NONE.equals(trace.getReserveStatus())) {
      return RESERVE_NONE;
    }
    return pendingStatus;
  }

  /**
   * 按下次检查时间（next_check_at）分批取出到期需要恢复的 trace。
   *
   * <p>INIT 只有在已过期或正处于预占中时才会被取出；否则大量未预占的旧 INIT 会永久占满每批的扫描名额。
   */
  public List<String> recoveryIds() {
    return mapper.recoveryIdsSql(Map.of());
  }

  /** 记录本次检查并推迟下一次检查时间；进度存在数据库里，实例重启后仍然保留。 */
  public void checked(String id, LocalDateTime next) {
    mapper.checkedSql(parameters("id", id, "next", next));
  }

  /** 异步积压（待建单数量与最早受理时间）。这是全表聚合，只能由后台定时采样调用，不能放在每次请求里。 */
  public Map<String, Object> backlog() {
    return RowValues.required(mapper.backlogSql(Map.of()));
  }

  /** 按待建单、待确认、待释放分别统计的异步积压，供后台监控采样。 */
  public List<Map<String, Object>> asyncBuildBacklog() {
    return mapper.asyncBuildBacklogSql();
  }

  /** 把 "键1, 值1, 键2, 值2..." 形式的参数组装成 Mapper 参数 Map；值保持原类型，允许为 null。 */
  private static Map<String, Object> parameters(Object... keyValuePairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      String key = (String) keyValuePairs[i];
      Object value = keyValuePairs[i + 1];
      values.put(key, value);
    }
    return values;
  }
}
