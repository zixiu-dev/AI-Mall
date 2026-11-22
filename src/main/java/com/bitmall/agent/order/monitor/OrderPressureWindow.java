package com.bitmall.agent.order.monitor;

import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 用十个按秒复用的桶统计本机下单压力，不保存单笔请求明细。
 *
 * <p>同步耗时按 10ms 分桶，P95 返回桶上界；5s 及以上进入最后一桶，统一返回 5010ms。
 */
@Component
public class OrderPressureWindow {
  private static final int WINDOW_SECONDS = 10;
  private static final int MILLIS_PER_SECOND = 1000;
  private static final int LATENCY_BUCKET_MILLIS = 10;
  private static final long LATENCY_BUCKET_NANOS = 10_000_000L;
  private static final int MAX_LATENCY_BUCKET_INDEX = 500;
  private static final int HISTOGRAM_SIZE = MAX_LATENCY_BUCKET_INDEX + 1;
  private static final double P95_QUANTILE = 0.95;

  /** 读取当前秒及启动后经过的时间；测试可传入可控时钟验证窗口过期。 */
  private final Clock clock;

  private final long createdAtMillis;
  private final Bucket[] buckets = new Bucket[WINDOW_SECONDS];

  public OrderPressureWindow() {
    this(Clock.systemUTC());
  }

  OrderPressureWindow(Clock clock) {
    this.clock = clock;
    createdAtMillis = clock.millis();
    for (int bucketIndex = 0; bucketIndex < WINDOW_SECONDS; bucketIndex++) {
      buckets[bucketIndex] = new Bucket();
    }
  }

  /** 按当前秒定位桶；同一位置每十秒复用一次，复用前清掉旧计数。调用方持有实例锁。 */
  private Bucket current() {
    long currentSecond = clock.millis() / MILLIS_PER_SECOND;
    int bucketIndex = (int) Math.floorMod(currentSecond, WINDOW_SECONDS);
    Bucket bucket = buckets[bucketIndex];
    if (bucket.second != currentSecond) {
      bucket.reset(currentSecond);
    }
    return bucket;
  }

  /** 请求量包含被并发门禁拒绝的请求，否则过载时反而会误判成低负载。 */
  public synchronized void request() {
    current().requests++;
  }

  /** 只由建单事务 afterCommit 回调调用；受理、回滚、重放都不增加成功建单数。 */
  public synchronized void orderCreated() {
    current().orders++;
  }

  /** 记录一次同步建单耗时；负值归入首桶，超出量程归入末桶。 */
  public synchronized void syncCompleted(long nanos) {
    Bucket bucket = current();
    int latencyBucketIndex =
        (int) Math.min(MAX_LATENCY_BUCKET_INDEX, Math.max(0, nanos / LATENCY_BUCKET_NANOS));
    bucket.samples++;
    bucket.histogram[latencyBucketIndex]++;
  }

  /** 合并最近十秒的有效桶，计算滚动平均 QPS/TPS 和同步耗时 P95。 */
  public synchronized Map<String, Object> snapshot() {
    long currentSecond = clock.millis() / MILLIS_PER_SECOND;
    long requestCount = 0;
    long sampleCount = 0;
    long orderCount = 0;
    long[] histogram = new long[HISTOGRAM_SIZE];
    for (Bucket bucket : buckets) {
      boolean withinWindow =
          bucket.second <= currentSecond && bucket.second > currentSecond - WINDOW_SECONDS;
      if (withinWindow) {
        requestCount += bucket.requests;
        sampleCount += bucket.samples;
        orderCount += bucket.orders;
        for (int bucketIndex = 0; bucketIndex < histogram.length; bucketIndex++) {
          histogram[bucketIndex] += bucket.histogram[bucketIndex];
        }
      }
    }
    int syncP95Millis = syncP95Millis(histogram, sampleCount);
    // 启动不足十秒时按实际时间计算，最小按一秒计，避免刚启动时除以零。
    double elapsedSeconds =
        Math.max(1, Math.min(WINDOW_SECONDS, (clock.millis() - createdAtMillis) / 1000.0));
    return Map.of(
        "qps", requestCount / elapsedSeconds,
        "tps", orderCount / elapsedSeconds,
        "windowSeconds", elapsedSeconds,
        "requestCount", requestCount,
        "ordersCreated", orderCount,
        "syncSamples", sampleCount,
        "syncP95Ms", syncP95Millis);
  }

  /** 从低耗时桶依次累计到第 95% 个样本；返回桶上界，无样本时返回零。 */
  private static int syncP95Millis(long[] histogram, long sampleCount) {
    long remainingSamples = (long) Math.ceil(sampleCount * P95_QUANTILE);
    if (sampleCount > 0) {
      for (int bucketIndex = 0; bucketIndex < histogram.length; bucketIndex++) {
        remainingSamples -= histogram[bucketIndex];
        if (remainingSamples <= 0) {
          return (bucketIndex + 1) * LATENCY_BUCKET_MILLIS;
        }
      }
    }
    return 0;
  }

  /** 一秒内的请求数、建单数和耗时直方图，由外层同步方法保护。 */
  private static class Bucket {
    long second = Long.MIN_VALUE;
    long requests;
    long samples;
    long orders;
    final long[] histogram = new long[HISTOGRAM_SIZE];

    void reset(long currentSecond) {
      second = currentSecond;
      requests = 0;
      samples = 0;
      orders = 0;
      Arrays.fill(histogram, 0);
    }
  }
}
