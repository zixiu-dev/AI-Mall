package com.bitmall.agent.common.exception;

/** 明确的业务条件拒绝，允许返回原因；继承原有状态异常以保持控制器兼容。 */
public class BusinessRejectedException extends IllegalStateException {
  public BusinessRejectedException(String message) { super(message); }
}
