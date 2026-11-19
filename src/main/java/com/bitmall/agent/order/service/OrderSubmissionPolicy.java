package com.bitmall.agent.order.service;

import com.bitmall.agent.order.config.OrderMessagingProperties;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.monitor.OrderPressureRouter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 按部署配置选择下单模式，AUTO 根据交易压力选择，浏览热点不参与交易路由。 */
@Service
public class OrderSubmissionPolicy {
  private final OrderProcessingProperties processing;
  private final OrderMessagingProperties messaging;
  private final OrderPressureRouter router;

  public OrderSubmissionPolicy(
      OrderProcessingProperties processing,
      OrderMessagingProperties messaging,
      OrderPressureRouter router) {
    this.router = router;
    this.processing = processing;
    this.messaging = messaging;
  }

  /** 准备购买时固定本次执行方式；旧 trace 不因配置切回同步而绕过已受理任务。 */
  public String prepareMode() {
    String mode = processing.getSubmissionMode();
    if ("AUTO".equals(mode)) return router.hint();
    if ("ASYNC".equals(mode)) requireQueueEnabled();
    return mode;
  }

  /** 首次预扣前选择，预占开始后重试始终复用持久化的模式。 */
  public String firstSubmissionMode(String preparedMode) {
    return "AUTO".equals(processing.getSubmissionMode()) ? router.select() : preparedMode;
  }

  /** 异步购买在预扣和受理前读取探针快照，短暂故障可用原购买编号重试。 */
  public void requireQueueAvailable() {
    requireQueueEnabled();
    if (!router.isAsyncReady())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "排队下单暂不可用，请稍后使用原购买编号重试");
  }

  private void requireQueueEnabled() {
    if (!messaging.isEnabled()) throw new IllegalStateException("排队下单暂未开放，请稍后使用原购买编号重试");
  }
}
