package com.bitmall.agent.agent.service.chat;

import com.bitmall.agent.agent.vo.StreamEventVO;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 单条回答的活跃草稿。锁保护快照、版本和发送队列顺序，网络写入由独立线程执行。 */
final class AgentAnswerStream {
  private static final int MAX_SUBSCRIBERS = 8;
  private static final int MAX_QUEUED_EVENTS = 64;

  private final String conversationId;
  private final Executor sender;
  private final long timeoutMillis;
  private final StringBuilder draft = new StringBuilder();
  private final Set<Subscriber> subscribers = new HashSet<>();
  private long revision;
  private StreamEventVO terminal;

  AgentAnswerStream(String conversationId, Executor sender, long timeoutMillis) {
    this.conversationId = conversationId;
    this.sender = sender;
    this.timeoutMillis = timeoutMillis;
  }

  /** 在同一个锁内把快照放到队首并注册订阅，保证下一段更新排在快照之后。 */
  synchronized SseEmitter subscribe() {
    if (subscribers.size() >= MAX_SUBSCRIBERS) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "此回答的实时连接过多");
    }
    Subscriber subscriber = new Subscriber();
    subscribers.add(subscriber);
    offer(subscriber, terminal == null ? snapshot() : terminal);
    return subscriber.emitter;
  }

  /** 更新草稿并登记增量，前端 reset 后重连也只能看到清空后的内容。 */
  synchronized void publish(StreamEventVO event) {
    if (terminal != null) {
      return;
    }
    switch (event.getType()) {
      case "delta" -> draft.append(event.getContent());
      case "reset" -> draft.setLength(0);
      default -> {
        // 工具进度共用递增版本，正文保持原样。
      }
    }
    StreamEventVO update = versioned(event);
    for (Subscriber subscriber : Set.copyOf(subscribers)) {
      if (subscriber.queue.size() >= MAX_QUEUED_EVENTS) {
        // 慢连接用更近的完整草稿追上进度，限制积压，后续版本仍按序追加。
        subscriber.queue.clear();
        offer(subscriber, snapshot());
      } else {
        offer(subscriber, update);
      }
    }
  }

  /** 数据库提交后发送终态；终态包含全文，可以替代拥塞队列中的旧草稿。 */
  synchronized void finish(StreamEventVO event) {
    terminal = versioned(event);
    draft.setLength(0);
    draft.trimToSize();
    for (Subscriber subscriber : Set.copyOf(subscribers)) {
      if (subscriber.queue.size() >= MAX_QUEUED_EVENTS) {
        subscriber.queue.clear();
      }
      offer(subscriber, terminal);
    }
  }

  private StreamEventVO snapshot() {
    return new StreamEventVO("snapshot", conversationId, draft.toString(), null, revision);
  }

  private StreamEventVO versioned(StreamEventVO event) {
    return new StreamEventVO(
        event.getType(), conversationId, event.getContent(), event.getReply(), ++revision);
  }

  /** 各订阅同一时刻最多有一个发送者，发送池饱和仅关闭受影响的连接。 */
  private void offer(Subscriber subscriber, StreamEventVO event) {
    subscriber.queue.addLast(event);
    if (subscriber.sending) {
      return;
    }
    subscriber.sending = true;
    try {
      sender.execute(() -> drain(subscriber));
    } catch (RejectedExecutionException busy) {
      detach(subscriber);
      subscriber.emitter.complete();
    }
  }

  private void drain(Subscriber subscriber) {
    try {
      while (true) {
        StreamEventVO update;
        boolean last;
        synchronized (this) {
          update = subscriber.queue.pollFirst();
          if (update == null) {
            subscriber.sending = false;
            return;
          }
          last = update == terminal;
        }
        subscriber.emitter.send(SseEmitter.event().name(update.getType())
            .id(Long.toString(update.getRevision())).data(update, MediaType.APPLICATION_JSON));
        if (last) {
          detach(subscriber);
          subscriber.emitter.complete();
          return;
        }
      }
    } catch (IOException | IllegalStateException disconnected) {
      detach(subscriber);
      subscriber.emitter.complete();
    }
  }

  /** 容器回调通过具体订阅对象移除旧连接，重连得到的订阅不受影响。 */
  private synchronized void detach(Subscriber subscriber) {
    subscribers.remove(subscriber);
    subscriber.queue.clear();
  }

  private final class Subscriber {
    private final SseEmitter emitter = new SseEmitter(timeoutMillis);
    private final ArrayDeque<StreamEventVO> queue = new ArrayDeque<>();
    private boolean sending;

    private Subscriber() {
      emitter.onCompletion(() -> detach(this));
      emitter.onError(error -> detach(this));
      emitter.onTimeout(() -> {
        detach(this);
        emitter.complete();
      });
    }
  }
}
