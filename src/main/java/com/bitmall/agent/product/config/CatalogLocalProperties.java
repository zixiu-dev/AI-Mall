package com.bitmall.agent.product.config;

import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 商品本地（L1）缓存策略（app.catalog-local）。 */
@Getter
@ConfigurationProperties("app.catalog-local")
public class CatalogLocalProperties {
  private static final Set<String> SUPPORTED_MODES = Set.of("HOTKEY", "ALL", "OFF");

  /**
   * 本地缓存模式，不区分大小写，默认 HOTKEY：
   *
   * <ul>
   *   <li>HOTKEY：只有被热点探测识别为热点的 key 才进本地缓存；
   *   <li>ALL：所有 key 都允许进本地缓存；
   *   <li>OFF：不用本地缓存，只走 Redis，用于压测对照。
   * </ul>
   */
  private final String mode;

  public CatalogLocalProperties(String mode) {
    this.mode = mode == null ? "HOTKEY" : mode.toUpperCase(Locale.ROOT);
    if (!SUPPORTED_MODES.contains(this.mode)) {
      throw new IllegalArgumentException("本地缓存模式只支持 HOTKEY、ALL、OFF");
    }
  }
}
