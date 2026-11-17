package com.bitmall.agent.order.mq.message;

/** 数据库事务内发布的本机通知；仅提交后尝试立即发送，丢失通知仍由 Outbox 扫描补漏。 */
public final class OutboxReadyEvent {
  private final String eventId;

  public OutboxReadyEvent(String eventId) {
    this.eventId = eventId;
  }

  public String getEventId() {
    return eventId;
  }
}
