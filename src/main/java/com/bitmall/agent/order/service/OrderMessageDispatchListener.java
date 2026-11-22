package com.bitmall.agent.order.service;

import com.bitmall.agent.order.mq.message.OutboxReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后异步唤醒发送，不让用户请求等待 Broker；进程退出或线程池满仍由扫描补漏。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderMessageDispatchListener {
  /** 读取待发送 Outbox，投递 RocketMQ，并保存发送或重试状态。 */
  private final OrderMessageRelayService relay;

  @Async("orderMessageDispatchExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReady(OutboxReadyEvent event) {
    relay.relayEvent(event.getEventId());
  }
}
