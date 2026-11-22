package com.bitmall.agent.order.dao.repository;

import com.bitmall.agent.order.dao.mapper.OrderEventReceiptMapper;
import com.bitmall.agent.order.mq.message.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.LinkedHashMap;
import java.util.Map;

/** 保存 MQ 消费后的订单事件审计回执。事件编号是主键，重复消息不能插入第二份。 */
@Repository
@RequiredArgsConstructor
public class OrderEventReceiptRepository {
  /** 订单事件回执的 SQL，定义在 mapper/order/OrderEventReceiptMapper.xml。 */
  private final OrderEventReceiptMapper mapper;

  /** 记录已消费事件；主键冲突由事务外的消费者识别为重复投递。 */
  public void insert(OrderEventMessage message) {
    mapper.insertSql(
        parameters(
            "id",
            message.getEventId(),
            "orderId",
            message.getOrderId(),
            "type",
            message.getEventType(),
            "userId",
            message.getUserId(),
            "amount",
            message.getPayAmount(),
            "time",
            message.getOccurredAt()));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
