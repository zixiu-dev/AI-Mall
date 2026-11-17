package com.bitmall.agent.product.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本地缓存策略：默认 HOTKEY 按真实热点提升；ALL 始终允许本地缓存，OFF 用于只走 Redis 或受控对照。 */
@ConfigurationProperties("app.catalog-local")
public class CatalogLocalProperties {
  private final String mode;

  public CatalogLocalProperties(String mode) {
    this.mode = mode == null ? "HOTKEY" : mode.toUpperCase(Locale.ROOT);
    if (!java.util.Set.of("HOTKEY", "ALL", "OFF").contains(this.mode))
      throw new IllegalArgumentException("本地缓存模式只支持 HOTKEY、ALL、OFF");
  }

  public String getMode() {
    return mode;
  }
}
