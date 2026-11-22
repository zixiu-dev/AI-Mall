package com.bitmall.agent.order.dao.entity;

import java.time.LocalDateTime;
import lombok.Getter;

/** 下单任务的持久化快照。只承载数据库字段，状态转换和事务在 Service 中实现。 */
@Getter
public final class OrderTraceDO {
  /** 后端生成的购买尝试编号。 */
  private final String traceId;

  /** 交易库存预占/整理阶段，与购买成功失败分开。 */
  private final String reserveStatus;

  /** 首次提交参数的摘要，只校验内容是否改变。 */
  private final String submissionHash;

  private final long userId;

  /** Agent 确认操作等上游稳定编号，没有时为空。 */
  private final String originKey;

  /** 固定的 SYNC 或 ASYNC 模式。 */
  private final String mode;

  /** 后端核算的结算快照 JSON。 */
  private final String requestSnapshot;

  /** 快照的 SHA-256 摘要。 */
  private final String requestHash;

  /** INIT、PROCESSING、SUCCESS、FAILED 或 EXPIRED。 */
  private final String status;

  /** 成功创建的订单编号。 */
  private final String orderId;

  /** 成功业务回执 JSON。 */
  private final String resultJson;

  /** 稳定的失败分类。 */
  private final String failureCode;

  private final String failureReason;

  /** 用于条件更新的版本号。 */
  private final int version;

  /** 未提交凭证的到期时间。 */
  private final LocalDateTime expiresAt;

  private final LocalDateTime createdAt;

  /** 实际受理时间，不包含用户确认停留。 */
  private final LocalDateTime acceptedAt;

  /** 异步受理后的建单截止时间；同步与旧历史记录为空。 */
  private final LocalDateTime processingExpiresAt;

  public OrderTraceDO(
      String traceId,
      long userId,
      String originKey,
      String mode,
      String requestSnapshot,
      String requestHash,
      String status,
      String orderId,
      String resultJson,
      String failureCode,
      String failureReason,
      int version,
      LocalDateTime expiresAt,
      LocalDateTime createdAt,
      LocalDateTime acceptedAt,
      LocalDateTime processingExpiresAt,
      String reserveStatus,
      String submissionHash) {
    this.traceId = traceId;
    this.reserveStatus = reserveStatus;
    this.submissionHash = submissionHash;
    this.userId = userId;
    this.originKey = originKey;
    this.mode = mode;
    this.requestSnapshot = requestSnapshot;
    this.requestHash = requestHash;
    this.status = status;
    this.orderId = orderId;
    this.resultJson = resultJson;
    this.failureCode = failureCode;
    this.failureReason = failureReason;
    this.version = version;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
    this.acceptedAt = acceptedAt;
    this.processingExpiresAt = processingExpiresAt;
  }
}
