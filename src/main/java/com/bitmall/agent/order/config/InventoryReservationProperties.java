package com.bitmall.agent.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 交易库存开关和未完成受理的恢复宽限期；商品展示缓存不受此配置影响。 */
@ConfigurationProperties("app.inventory")
public class InventoryReservationProperties {
  private final boolean enabled;
  private final int admissionGraceSeconds;

  public InventoryReservationProperties(Boolean enabled, int admissionGraceSeconds) {
    this.enabled = Boolean.TRUE.equals(enabled);
    this.admissionGraceSeconds = admissionGraceSeconds > 0 ? admissionGraceSeconds : 30;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getAdmissionGraceSeconds() {
    return admissionGraceSeconds;
  }
}
