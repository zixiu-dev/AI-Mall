package com.bitmall.agent.order.service;

import com.bitmall.agent.order.dao.repository.OrderEventReceiptRepository;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 消费订单事件的业务入口。当前用途是保存异步审计回执，不重复扣库存、建单，也不改变订单当前状态。 */
@Service
public class OrderEventReceiptService {
  /** 消费回执的数据访问组件。 */
  private final OrderEventReceiptRepository mapper;

  /** 注入回执持久化组件。 */
  public OrderEventReceiptService(OrderEventReceiptRepository mapper) {
    this.mapper = mapper;
  }

  /** 事务提交后才返回消费者；数据库失败时抛异常，让 Broker 重试投递。 */
  @Transactional
  public void record(OrderEventMessage message) {
    mapper.insert(message);
  }
}
