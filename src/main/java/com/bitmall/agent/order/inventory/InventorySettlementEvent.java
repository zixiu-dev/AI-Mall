package com.bitmall.agent.order.inventory;

/** 事务内发布、提交后整理库存；trace 事件处理确认/失败释放，order 事件处理关单返还。 */
public final class InventorySettlementEvent {
  private final String traceId;
  private final String orderId;

  public InventorySettlementEvent(String traceId, String orderId) {
    this.traceId = traceId;
    this.orderId = orderId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getOrderId() {
    return orderId;
  }
}
