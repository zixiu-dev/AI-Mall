package com.bitmall.agent.common.monitor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 固定一秒桶：每次响应完成、每次建单事务提交时直接记账，不把轮询间隔平均值当成一秒峰值。 */
@Component
public class OneSecondThroughput {
  private final boolean enabled;
  private final Clock clock;
  private final AtomicReferenceArray<Bucket> buckets;

  /** 默认关闭详细秒桶，压测实例显式开启；最多保留最近半小时，避免内存随运行时间增长。 */
  @Autowired
  public OneSecondThroughput(@Value("${app.throughput.enabled:false}") boolean enabled) {
    this(enabled, Clock.systemUTC(), 1800);
  }

  /** 测试使用可控时钟，准确验证整秒边界，不依赖 Thread.sleep。 */
  OneSecondThroughput(boolean enabled, Clock clock, int retentionSeconds) {
    if (retentionSeconds < 2) throw new IllegalArgumentException("至少保留两个秒桶");
    this.enabled = enabled;
    this.clock = clock;
    this.buckets = new AtomicReferenceArray<>(retentionSeconds);
  }

  /** 只统计已完成的商品详情/下单响应；HTTP 2xx 与所有响应分开，429 不能冒充成功。 */
  public void httpCompleted(boolean productQuery, int status) {
    if (!enabled) return;
    Bucket bucket = current();
    (productQuery ? bucket.queryResponses : bucket.submitResponses).increment();
    if (status >= 200 && status < 300)
      (productQuery ? bucket.querySuccess : bucket.submitSuccess).increment();
  }

  /** 由 OrderMetrics 的 afterCommit 调用；受理、真正建单分别记账，重复消息不在这里虚增订单。 */
  public void orderTransition(String event, String mode) {
    if (!enabled) return;
    if ("created".equals(event)) {
      Bucket bucket = current();
      ("ASYNC".equals(mode) ? bucket.createdAsync : bucket.createdSync).increment();
    } else if ("accepted".equals(event) && "ASYNC".equals(mode)) current().accepted.increment();
  }

  /** 缓存重建也按事件发生秒记账；热缓存命中不进入这个统计分支，避免逐 key 标签。 */
  public void cacheEvent(String event) {
    if (!enabled) return;
    switch (event) {
      case "database_load" -> current().cacheLoads.increment();
      case "rebuild_filled" -> current().cacheFills.increment();
      case "rebuild_wait" -> current().cacheRedisWaits.increment();
      case "rebuild_avoided_after_local_lock", "rebuild_avoided_after_redis_lock" ->
          current().cacheDoubleCheckHits.increment();
      default -> {
        /* 本地/Redis 普通命中沿用累计计数，不为每个事件增加秒桶字段。 */
      }
    }
  }

  /** 每个线程按事件发生时间选择桶；LongAdder 避免所有请求竞争同一把计数锁。 */
  private Bucket current() {
    long second = Math.floorDiv(clock.millis(), 1000);
    int index = Math.floorMod(second, buckets.length());
    while (true) {
      Bucket old = buckets.get(index);
      if (old != null && old.second == second) return old;
      Bucket replacement = new Bucket(second);
      if (buckets.compareAndSet(index, old, replacement)) return replacement;
    }
  }

  /** 只返回已经结束的完整一秒；保留原始桶，峰值由压测脚本在实际发压区间内取最大值。 */
  public Map<String, Object> snapshot() {
    long now = clock.millis();
    long openSecond = Math.floorDiv(now, 1000);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (long second = openSecond - buckets.length() + 1; second < openSecond; second++) {
      Bucket bucket = buckets.get(Math.floorMod(second, buckets.length()));
      if (bucket != null && bucket.second == second) rows.add(bucket.view());
    }
    return Map.of(
        "enabled", enabled,
        "bucketWidthMillis", 1000,
        "alignment", "UTC epoch [second*1000, (second+1)*1000)",
        "snapshotAtEpochMs", now,
        "seconds", rows);
  }

  /** 一个固定自然秒的有限指标，不记录用户、SKU、trace 等高基数信息。 */
  private static class Bucket {
    private final long second;
    private final LongAdder queryResponses = new LongAdder();
    private final LongAdder querySuccess = new LongAdder();
    private final LongAdder submitResponses = new LongAdder();
    private final LongAdder submitSuccess = new LongAdder();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder createdSync = new LongAdder();
    private final LongAdder createdAsync = new LongAdder();
    private final LongAdder cacheLoads = new LongAdder();
    private final LongAdder cacheFills = new LongAdder();
    private final LongAdder cacheRedisWaits = new LongAdder();
    private final LongAdder cacheDoubleCheckHits = new LongAdder();

    private Bucket(long second) {
      this.second = second;
    }

    private Map<String, Object> view() {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("epochSecond", second);
      row.put("queryResponses", queryResponses.sum());
      row.put("querySuccess", querySuccess.sum());
      row.put("submitResponses", submitResponses.sum());
      row.put("submitHttpSuccess", submitSuccess.sum());
      row.put("asyncAccepted", accepted.sum());
      row.put("createdSync", createdSync.sum());
      row.put("createdAsync", createdAsync.sum());
      row.put("ordersCreated", createdSync.sum() + createdAsync.sum());
      row.put("cacheLoads", cacheLoads.sum());
      row.put("cacheFills", cacheFills.sum());
      row.put("cacheRedisWaits", cacheRedisWaits.sum());
      row.put("cacheDoubleCheckHits", cacheDoubleCheckHits.sum());
      return row;
    }
  }
}
