package com.bitmall.agent.agent.gateway.model;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** 单实例内一个模型的熔断状态。只锁定状态更新，远端调用在锁外执行。 */
final class ModelCircuitBreaker {
  enum State { CLOSED, OPEN, HALF_OPEN }

  enum Outcome { SUCCESS, FAILURE, NEUTRAL }

  /** 调用开始时的状态版本，用于忽略切换状态前发出的请求的迟到结果。 */
  static final class Permit {
    private final long version;

    private Permit(long version) {
      this.version = version;
    }
  }

  private final int failureThreshold;
  private final long cooldownNanos;
  private final LongSupplier clock;
  private State state = State.CLOSED;
  private long version;
  private long openedAt;
  private int consecutiveFailures;

  ModelCircuitBreaker(int failureThreshold, Duration cooldown, LongSupplier clock) {
    if (failureThreshold < 1 || cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException("模型熔断阈值与冷却时间必须为正数");
    }
    this.failureThreshold = failureThreshold;
    this.cooldownNanos = cooldown.toNanos();
    this.clock = Objects.requireNonNull(clock);
  }

  /** 冷却到期后第一个业务请求获得半开探测许可，其他请求仍跳过该模型。 */
  synchronized Permit acquire() {
    if (state == State.HALF_OPEN) {
      return null;
    }
    if (state == State.OPEN) {
      if (clock.getAsLong() - openedAt < cooldownNanos) {
        return null;
      }
      state = State.HALF_OPEN;
      version++;
    }
    return new Permit(version);
  }

  /** 半开探测决定下一状态；普通调用按完成顺序累计连续故障。 */
  synchronized void complete(Permit permit, Outcome outcome) {
    if (permit.version != version) {
      return;
    }
    if (state == State.HALF_OPEN) {
      if (outcome == Outcome.SUCCESS) {
        state = State.CLOSED;
        consecutiveFailures = 0;
        version++;
      } else if (outcome == Outcome.FAILURE) {
        open();
      } else {
        // 冷却已结束，下一个请求可立刻获取新的探测许可。
        state = State.OPEN;
        version++;
      }
      return;
    }
    if (outcome == Outcome.SUCCESS) {
      consecutiveFailures = 0;
    } else if (outcome == Outcome.FAILURE && ++consecutiveFailures >= failureThreshold) {
      open();
    }
  }

  synchronized State state() {
    return state;
  }

  private void open() {
    state = State.OPEN;
    openedAt = clock.getAsLong();
    consecutiveFailures = 0;
    version++;
  }
}
