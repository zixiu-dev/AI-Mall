package com.bitmall.agent.order.service;

import com.bitmall.agent.order.mq.message.OutboxReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 事务提交后异步唤醒发送，不让用户请求等待 Broker；进程退出或线程池满仍由扫描补漏。 */
@Component
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
public class OrderMessageDispatchListener {
  private final OrderMessageRelayService relay;

  public OrderMessageDispatchListener(OrderMessageRelayService relay) {
    this.relay = relay;
  }

  @Async("orderMessageDispatchExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReady(OutboxReadyEvent event) {
    relay.relayEvent(event.getEventId());
  }
}
