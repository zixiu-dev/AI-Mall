package com.bitmall.agent.order.exception;

/** 明确的业务拒绝，与连接或数据库故障区分；临时故障不能直接终止任务并返还库存。 */
public class OrderRejectedException extends RuntimeException {
  /** 稳定的失败分类。 */
  private final String code;

  /** 保存失败分类和用户提示。 */
  public OrderRejectedException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** 获取失败分类。 */
  public String getCode() {
    return code;
  }
}
