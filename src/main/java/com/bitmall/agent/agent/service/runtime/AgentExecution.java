package com.bitmall.agent.agent.service.runtime;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 一轮聊天请求的取消信号和时间预算。
 *
 * <p>两种停止方式：
 *
 * <ul>
 *   <li>取消：客户端断开连接时调用 {@link #cancel()}，中断执行线程；
 *   <li>超时：整轮有总预算，每次模型/工具调用（{@link #stage}）还有自己的预算，到期后由定时任务中断执行线程。
 * </ul>
 *
 * <p>只会中断通过 {@link #bind()} 绑定的那个执行线程。业务代码可能忽略中断继续跑，所以会话门禁要等执行线程真正退出后才释放， 而不是一发出取消就释放。
 *
 * <p>所有可变状态都只在 synchronized 块里读写。
 */
public final class AgentExecution implements AutoCloseable {
  /** 共享的定时线程池（由 AgentExecutionConfiguration 提供），用来在单次调用超时时中断执行线程。 */
  private final ScheduledExecutorService scheduler;

  /** 整轮请求的截止时间（System.nanoTime）。 */
  private final long deadline;

  /** 执行本轮请求的线程；bind 之前和 close 之后为 null。 */
  private Thread owner;

  /** 客户端是否已断开连接。 */
  private boolean cancelled;

  /** 本轮是否已结束。 */
  private boolean closed;

  /** 正在进行的单次调用；定时任务只在它仍是当前调用时才中断线程，防止过期的定时任务误伤下一次调用。 */
  private Stage active;

  public AgentExecution(ScheduledExecutorService scheduler, int timeoutSeconds) {
    this.scheduler = scheduler;
    this.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
  }

  /** 工作线程真正开始执行时绑定自己。如果请求在排队期间已经断开，这里直接抛出，不会再创建会话。 */
  public synchronized void bind() {
    check();
    if (owner != null) {
      throw new IllegalStateException("请求已经开始执行");
    }
    owner = Thread.currentThread();
  }

  /**
   * 只检查连接是否还在，不检查总预算。
   *
   * <p>与 {@link #check()} 分开，是因为超时后还需要通过现有连接把"处理超时"的提示发给用户。
   */
  public synchronized void checkConnected() {
    if (cancelled || closed || Thread.currentThread().isInterrupted()) {
      throw new CancellationException("聊天请求已停止");
    }
  }

  /** 检查连接仍在且总预算未用完；在每个步骤开始前调用。 */
  public synchronized void check() {
    checkConnected();
    if (System.nanoTime() >= deadline) {
      throw new IllegalStateException("Agent 总执行时间超限");
    }
  }

  /**
   * 在时间限制内执行一次模型或工具调用。
   *
   * <p>实际限时取"本次调用的超时"和"整轮剩余预算"中较小的一个。到期时中断执行线程，让阻塞中的调用尽快返回； 调用结束后如果发现已经超时或被取消，抛出异常，不让后续步骤继续执行。
   */
  public <T> T stage(String name, int timeoutSeconds, Supplier<T> work) {
    Stage stage = new Stage();
    ScheduledFuture<?> timer;
    synchronized (this) {
      check();
      active = stage;
      long remainingBudget = deadline - System.nanoTime();
      long budget = Math.min(TimeUnit.SECONDS.toNanos(timeoutSeconds), remainingBudget);
      timer = scheduler.schedule(() -> expire(stage), Math.max(0, budget), TimeUnit.NANOSECONDS);
    }
    try {
      T result = work.get();
      checkStage(stage, name);
      return result;
    } catch (RuntimeException error) {
      // 调用失败时，优先报告"超时/已取消"，而不是被中断引发的次生异常。
      checkStage(stage, name);
      throw error;
    } finally {
      synchronized (this) {
        active = null;
        timer.cancel(false);
        if (stage.expired) {
          // 清掉本次超时留下的中断标记，避免影响随后发送超时提示。
          Thread.interrupted();
        }
      }
    }
  }

  /** 定时任务回调：如果这次调用仍在进行，标记超时并中断执行线程。 */
  private synchronized void expire(Stage stage) {
    if (active == stage && !closed && owner != null) {
      stage.expired = true;
      owner.interrupt();
    }
  }

  private synchronized void checkStage(Stage stage, String name) {
    if (cancelled) {
      throw new CancellationException("聊天连接已断开");
    }
    if (stage.expired) {
      throw new IllegalStateException(name + "超时");
    }
    check();
  }

  /** 客户端断开时调用：发出取消信号并中断执行线程。业务代码忽略中断时，会话门禁仍会等它实际退出。 */
  public synchronized void cancel() {
    if (closed) {
      return;
    }
    cancelled = true;
    if (owner != null) {
      owner.interrupt();
    }
  }

  /** 本轮结束。由执行线程自己调用时，顺便清掉它身上残留的中断标记，避免影响线程池里的下一个任务。 */
  @Override
  public synchronized void close() {
    closed = true;
    if (owner == Thread.currentThread()) {
      Thread.interrupted();
    }
    owner = null;
  }

  /** 一次调用的超时标记，只在 synchronized 块中读写。 */
  private static final class Stage {
    private boolean expired;
  }
}
