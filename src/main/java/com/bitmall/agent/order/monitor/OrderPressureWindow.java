package com.bitmall.agent.order.monitor;

import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 十秒滚动窗口，不保留请求明细。耗时按 10ms 分桶，P95 是桶上界，超过 5s 计入最后一桶。 */
@Component
public class OrderPressureWindow {
  private static final int SECONDS = 10;
  private final Clock clock;
  private final long created;
  private final Bucket[] buckets = new Bucket[SECONDS];

  public OrderPressureWindow() {
    this(Clock.systemUTC());
  }

  OrderPressureWindow(Clock clock) {
    this.clock = clock;
    created = clock.millis();
    for (int i = 0; i < SECONDS; i++) buckets[i] = new Bucket();
  }

  private Bucket current() {
    long second = clock.millis() / 1000;
    Bucket b = buckets[(int) Math.floorMod(second, SECONDS)];
    if (b.second != second) {
      b.second = second;
      b.requests = 0;
      b.samples = 0;
      b.orders = 0;
      java.util.Arrays.fill(b.histogram, 0);
    }
    return b;
  }

  /** 请求量包含被并发门禁拒绝的请求，否则过载时反而会误判成低负载。 */
  public synchronized void request() {
    current().requests++;
  }

  /** 只由建单事务 afterCommit 回调调用；受理、回滚、重放都不增加成功建单数。 */
  public synchronized void orderCreated() {
    current().orders++;
  }

  public synchronized void syncCompleted(long nanos) {
    var b = current();
    b.samples++;
    b.histogram[(int) Math.min(500, Math.max(0, nanos / 10_000_000))]++;
  }

  public synchronized Map<String, Object> snapshot() {
    long now = clock.millis() / 1000, requests = 0, samples = 0, orders = 0;
    long[] histogram = new long[501];
    for (var b : buckets)
      if (b.second <= now && b.second > now - SECONDS) {
        requests += b.requests;
        samples += b.samples;
        orders += b.orders;
        for (int i = 0; i < histogram.length; i++) histogram[i] += b.histogram[i];
      }
    long remaining = (long) Math.ceil(samples * .95);
    int p95 = 0;
    if (samples > 0)
      for (int i = 0; i < histogram.length; i++) {
        remaining -= histogram[i];
        if (remaining <= 0) {
          p95 = (i + 1) * 10;
          break;
        }
      }
    double elapsed = Math.max(1, Math.min(SECONDS, (clock.millis() - created) / 1000.0));
    return Map.of(
        "qps",
        requests / elapsed,
        "tps",
        orders / elapsed,
        "windowSeconds",
        elapsed,
        "requestCount",
        requests,
        "ordersCreated",
        orders,
        "syncSamples",
        samples,
        "syncP95Ms",
        p95);
  }

  private static class Bucket {
    long second = Long.MIN_VALUE, requests, samples, orders;
    final long[] histogram = new long[501];
  }
}
