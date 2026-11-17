package com.bitmall.agent.order.dao.entity;

import java.time.LocalDateTime;

/** 下单任务的持久化快照。只承载数据库字段，状态转换和事务在 Service 中实现。 */
public final class OrderTraceDO {
  /** 后端生成的购买尝试编号。 */
  private final String traceId;

  /** 交易库存预占/整理阶段，与购买成功失败分开。 */
  private final String reserveStatus;

  /** 首次提交参数的摘要，只校验内容是否改变。 */
  private final String submissionHash;

  /** 所属用户编号。 */
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

  /** 可阅读的失败原因。 */
  private final String failureReason;

  /** 用于条件更新的版本号。 */
  private final int version;

  /** 未提交凭证的到期时间。 */
  private final LocalDateTime expiresAt;

  /** 创建时间。 */
  private final LocalDateTime createdAt;

  /** 实际受理时间，不包含用户确认停留。 */
  private final LocalDateTime acceptedAt;

  /** 异步受理后的建单截止时间；同步与旧历史记录为空。 */
  private final LocalDateTime processingExpiresAt;

  /** 显式保存全部字段，读取时使用下方 Getter。 */
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

  /** 获取交易库存预占/整理进度。 */
  public String getReserveStatus() {
    return reserveStatus;
  }

  /** 获取首次提交参数摘要，用于检查同编号的购买内容是否被更改。 */
  public String getSubmissionHash() {
    return submissionHash;
  }

  /** 获取后端生成的购买尝试编号。 */
  public String getTraceId() {
    return traceId;
  }

  /** 获取所属用户编号。 */
  public long getUserId() {
    return userId;
  }

  /** 获取Agent 确认操作等上游稳定编号，没有时为空。 */
  public String getOriginKey() {
    return originKey;
  }

  /** 获取固定的 SYNC 或 ASYNC 模式。 */
  public String getMode() {
    return mode;
  }

  /** 获取后端核算的结算快照 JSON。 */
  public String getRequestSnapshot() {
    return requestSnapshot;
  }

  /** 获取快照的 SHA-256 摘要。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 获取INIT、PROCESSING、SUCCESS、FAILED 或 EXPIRED。 */
  public String getStatus() {
    return status;
  }

  /** 获取成功创建的订单编号。 */
  public String getOrderId() {
    return orderId;
  }

  /** 获取成功业务回执 JSON。 */
  public String getResultJson() {
    return resultJson;
  }

  /** 获取稳定的失败分类。 */
  public String getFailureCode() {
    return failureCode;
  }

  /** 获取可阅读的失败原因。 */
  public String getFailureReason() {
    return failureReason;
  }

  /** 获取用于条件更新的版本号。 */
  public int getVersion() {
    return version;
  }

  /** 获取未提交凭证的到期时间。 */
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  /** 获取创建时间。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getProcessingExpiresAt() {
    return processingExpiresAt;
  }

  /** 获取实际受理时间，不包含用户确认停留。 */
  public LocalDateTime getAcceptedAt() {
    return acceptedAt;
  }
}
