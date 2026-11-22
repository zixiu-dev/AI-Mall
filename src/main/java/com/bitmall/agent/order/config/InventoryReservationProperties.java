package com.bitmall.agent.order.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 交易库存预占配置（app.inventory）。只影响下单链路，商品展示缓存不受此配置影响。 */
@Getter
@ConfigurationProperties("app.inventory")
public class InventoryReservationProperties {
  /** 是否启用 Redis 库存预占；未配置时为 false，下单直接扣数据库库存。 */
  private final boolean enabled;

  /**
   * 预占恢复宽限期（秒），默认 30。登记预占意图后，超过这个时间 trace 仍未完成受理（还是 INIT），
   * 就视为受理中断，恢复任务（OrderRecoveryService）会把这笔购买标记为失败（ADMISSION_INTERRUPTED）并释放预占。
   */
  private final int admissionGraceSeconds;

  public InventoryReservationProperties(Boolean enabled, int admissionGraceSeconds) {
    this.enabled = Boolean.TRUE.equals(enabled);
    this.admissionGraceSeconds = admissionGraceSeconds > 0 ? admissionGraceSeconds : 30;
  }
}
