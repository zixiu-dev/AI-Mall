package com.bitmall.agent.product.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** 记录缓存运行计数，例如本机命中、Redis 命中、数据库回源和失败次数。 既保留可查询的计数，也提交给 Micrometer 指标系统。 */
@Component
public class CacheMetrics {
  /** 每种缓存事件的并发计数器，LongAdder 减少多线程累加时的竞争。 */
  private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

  /** Micrometer 监控注册器，把项目计数接入应用指标系统。 */
  private final MeterRegistry registry;

  /** 可选的固定一秒统计，用来区分峰值秒是否真正发生了数据库回源。 */
  private final com.bitmall.agent.common.monitor.OneSecondThroughput seconds;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public CacheMetrics(MeterRegistry registry) {
    this(registry, null);
  }

  /** 生产装配使用统一秒桶；功能默认关闭，不建立逐商品高基数指标。 */
  @org.springframework.beans.factory.annotation.Autowired
  public CacheMetrics(
      MeterRegistry registry, com.bitmall.agent.common.monitor.OneSecondThroughput seconds) {
    this.registry = registry;
    this.seconds = seconds;
  }

  /** 累加指定事件的计数，同时向监控系统记录一次事件。 */
  public void increment(String event) {
    counts.computeIfAbsent(event, k -> new LongAdder()).increment();
    registry.counter("mall.catalog.cache", "event", event).increment();
    if (seconds != null) seconds.cacheEvent(event);
  }

  /** 复制当前各项计数，供状态接口查看，避免直接暴露可修改的内部计数器。 */
  public Map<String, Long> snapshot() {
    Map<String, Long> result = new LinkedHashMap<>();
    counts.keySet().stream().sorted().forEach(key -> result.put(key, counts.get(key).sum()));
    return result;
  }
}
