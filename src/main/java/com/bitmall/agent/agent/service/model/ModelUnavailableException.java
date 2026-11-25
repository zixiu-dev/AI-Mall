package com.bitmall.agent.agent.service.model;

/** 当前推理没有获得可用响应，聊天层可据此恢复已经保存的业务回执。 */
public final class ModelUnavailableException extends IllegalStateException {
  public ModelUnavailableException() {
    super("AI 服务暂时不可用，请稍后重试。");
  }
}
