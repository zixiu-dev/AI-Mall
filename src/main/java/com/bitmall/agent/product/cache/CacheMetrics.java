package com.bitmall.agent.product.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.bitmall.agent.common.monitor.OneSecondThroughput;
import org.springframework.beans.factory.annotation.Autowired;

/** 记录缓存运行计数，例如本机命中、Redis 命中、数据库回源和失败次数。 既保留可查询的计数，也提交给 Micrometer 指标系统。 */
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CacheMetrics {
  /** 每种缓存事件的并发计数器，LongAdder 减少多线程累加时的竞争。 */
  private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

  /** 按事件名注册缓存计数器（mall.catalog.cache）。 */
  private final MeterRegistry registry;

  /** 可为 null；存在时把缓存事件同时交给秒级吞吐统计（它只按秒记录回源、重建类事件）。 */
  private final OneSecondThroughput seconds;

  /** 便捷构造入口，供只验证缓存累计指标的测试使用。 */
  public CacheMetrics(MeterRegistry registry) {
    this(registry, null);
  }

  /** 累加指定事件的计数，同时向监控系统记录一次事件。 */
  public void increment(String event) {
    counts.computeIfAbsent(event, k -> new LongAdder()).increment();
    registry.counter("mall.catalog.cache", "event", event).increment();
    if (seconds != null) {
      seconds.cacheEvent(event);
    }
  }

  /** 复制当前各项计数，供状态接口查看，避免直接暴露可修改的内部计数器。 */
  public Map<String, Long> snapshot() {
    Map<String, Long> result = new LinkedHashMap<>();
    counts.keySet().stream().sorted().forEach(key -> result.put(key, counts.get(key).sum()));
    return result;
  }
}
