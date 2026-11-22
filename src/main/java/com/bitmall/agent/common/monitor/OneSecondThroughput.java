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

/**
 * 按自然秒统计吞吐量，供压测时读取每一秒的真实峰值。
 *
 * <p>每次 HTTP 响应完成、每次建单事务提交时立即计入当前秒的桶，而不是用轮询间隔内的平均值，否则会把瞬时峰值摊平。 桶数组是环形的，只保留最近 N 秒，内存不会随运行时间增长。
 */
@Component
public class OneSecondThroughput {
  private static final long MILLIS_PER_SECOND = 1000;

  /** 生产默认保留最近半小时的秒桶。 */
  private static final int DEFAULT_RETENTION_SECONDS = 1800;

  private static final String EVENT_CREATED = "created";
  private static final String EVENT_ACCEPTED = "accepted";
  private static final String MODE_ASYNC = "ASYNC";

  /** 是否开启秒级统计（app.throughput.enabled）；关闭时所有记账方法直接返回。 */
  private final boolean enabled;

  /** 取当前时间以确定秒桶；测试里换成可控时钟。 */
  private final Clock clock;

  /** 环形秒桶：下标 = 秒数 % 长度，旧秒的桶在被复用时整体替换。 */
  private final AtomicReferenceArray<Bucket> buckets;

  /** 默认关闭，只在压测实例上显式开启。 */
  @Autowired
  public OneSecondThroughput(@Value("${app.throughput.enabled:false}") boolean enabled) {
    this(enabled, Clock.systemUTC(), DEFAULT_RETENTION_SECONDS);
  }

  /** 测试用构造器：传入可控时钟，精确验证整秒边界而不依赖 Thread.sleep。 */
  OneSecondThroughput(boolean enabled, Clock clock, int retentionSeconds) {
    // 至少需要"当前秒"和"上一个完整秒"两个桶，snapshot 才有数据可返回。
    if (retentionSeconds < 2) {
      throw new IllegalArgumentException("至少保留两个秒桶");
    }
    this.enabled = enabled;
    this.clock = clock;
    this.buckets = new AtomicReferenceArray<>(retentionSeconds);
  }

  /**
   * 记录一次已完成的商品详情或下单 HTTP 响应。
   *
   * <p>"所有响应"和"2xx 响应"分开计数，这样 429 限流等失败响应不会被算成成功吞吐。
   */
  public void httpCompleted(boolean productQuery, int status) {
    if (!enabled) {
      return;
    }
    Bucket bucket = current();
    boolean success = status >= 200 && status < 300;
    if (productQuery) {
      bucket.queryResponses.increment();
      if (success) {
        bucket.querySuccess.increment();
      }
    } else {
      bucket.submitResponses.increment();
      if (success) {
        bucket.submitSuccess.increment();
      }
    }
  }

  /**
   * 记录订单状态变化，由 OrderMetrics 在事务提交后调用。
   *
   * <p>"异步受理"和"真正建单"分开计数；只在事务提交后记账，所以重复投递的消息不会让建单数虚增。
   */
  public void orderTransition(String event, String mode) {
    if (!enabled) {
      return;
    }
    boolean async = MODE_ASYNC.equals(mode);
    if (EVENT_CREATED.equals(event)) {
      Bucket bucket = current();
      if (async) {
        bucket.createdAsync.increment();
      } else {
        bucket.createdSync.increment();
      }
    } else if (EVENT_ACCEPTED.equals(event) && async) {
      current().accepted.increment();
    }
  }

  /**
   * 记录缓存回源和重建事件。
   *
   * <p>本地/Redis 普通命中量很大，只走累计计数器，不进入秒桶，也不为每个 key 打标签。
   */
  public void cacheEvent(String event) {
    if (!enabled) {
      return;
    }
    switch (event) {
      case "database_load" -> current().cacheLoads.increment();
      case "rebuild_filled" -> current().cacheFills.increment();
      case "rebuild_wait" -> current().cacheRedisWaits.increment();
      case "rebuild_avoided_after_local_lock", "rebuild_avoided_after_redis_lock" ->
          current().cacheDoubleCheckHits.increment();
      default -> {
        // 普通命中等其他事件不按秒统计。
      }
    }
  }

  /**
   * 取得当前自然秒对应的桶；该位置上还是旧秒的桶时，用 CAS 换成新桶。
   *
   * <p>多个线程同时发现需要换桶时，只有一个 CAS 成功，其余线程重试后会读到这个新桶。 计数器用 LongAdder，避免高并发下所有请求争抢同一个计数。
   */
  private Bucket current() {
    long second = Math.floorDiv(clock.millis(), MILLIS_PER_SECOND);
    int index = Math.floorMod(second, buckets.length());
    while (true) {
      Bucket existing = buckets.get(index);
      if (existing != null && existing.second == second) {
        return existing;
      }
      Bucket replacement = new Bucket(second);
      if (buckets.compareAndSet(index, existing, replacement)) {
        return replacement;
      }
    }
  }

  /**
   * 返回保留窗口内所有已经结束的完整秒。
   *
   * <p>当前这一秒还在累计，不返回，否则会出现偏低的半秒数据。这里只返回原始秒桶，峰值由压测脚本在实际发压区间内自行取最大值。
   */
  public Map<String, Object> snapshot() {
    long now = clock.millis();
    long currentSecond = Math.floorDiv(now, MILLIS_PER_SECOND);
    long oldestSecond = currentSecond - buckets.length() + 1;
    List<Map<String, Object>> rows = new ArrayList<>();
    for (long second = oldestSecond; second < currentSecond; second++) {
      Bucket bucket = buckets.get(Math.floorMod(second, buckets.length()));
      // 桶位置可能已被更新的秒复用，或那一秒没有任何事件。
      boolean bucketBelongsToSecond = bucket != null && bucket.second == second;
      if (bucketBelongsToSecond) {
        rows.add(bucket.view());
      }
    }
    return Map.of(
        "enabled", enabled,
        "bucketWidthMillis", 1000,
        "alignment", "UTC epoch [second*1000, (second+1)*1000)",
        "snapshotAtEpochMs", now,
        "seconds", rows);
  }

  /** 某一个自然秒内的计数。只记录少量固定指标，不记录用户、SKU、trace 等高基数信息。 */
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
