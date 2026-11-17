package com.bitmall.agent.order.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.dao.entity.OrderMessageDO;
import com.bitmall.agent.order.dao.mapper.OrderMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 本地订单消息的 SQL。条件抢占和租约令牌防止多个实例同时确认同一条发送结果；这里不连接 MQ。 */
@Repository
public class OrderMessageRepository {
  /** 与 trace PROCESSING 一起提交，消息只传后端编号。 */
  public void insertBuild(String eventId, String traceId, String topic, String payload) {
    mapper.insertBuildSql(
        parameters("id", eventId, "trace", traceId, "topic", topic, "payload", payload));
  }

  /** 提交后快速发送指定事件；实际发送前仍必须条件抢占租约。 */
  public java.util.Optional<OrderMessageDO> findById(String id) {
    return mapper.findByIdSql(parameters("id", id)).stream()
        .findFirst()
        .map(
            row ->
                new OrderMessageDO(
                    RowValues.string(row, "event_id"),
                    RowValues.string(row, "topic"),
                    RowValues.string(row, "event_type"),
                    RowValues.string(row, "payload"),
                    RowValues.intValue(row, "attempts"),
                    row.get("deliver_at_epoch_ms") == null
                        ? null : RowValues.longValue(row, "deliver_at_epoch_ms")));
  }

  /** 恢复重投保留业务编号，消费者仍根据数据库终态去重。 */
  public void requeueBuild(String trace, LocalDateTime cutoff) {
    mapper.requeueBuildSql(parameters("trace", trace, "cutoff", cutoff));
  }

  /** 只取消尚未完成发送的建单命令；明确记录 CANCELLED，不冒充成功发送。 */
  public void cancelBuild(String trace) {
    mapper.cancelBuildSql(parameters("trace", trace));
  }

  /** 本地消息表使用与订单相同的数据源和事务。 */
  private final OrderMessageMapper mapper;

  /** 由 Spring 注入数据库访问工具。 */
  public OrderMessageRepository(OrderMessageMapper mapper) {
    this.mapper = mapper;
  }

  /** 在订单事务中保存待发送事件；订单回滚时这条消息也会回滚。 */
  public void insert(
      String eventId, String orderId, String topic, String eventType, String payload) {
    insert(eventId, orderId, topic, eventType, payload, null);
  }

  /** 与订单同事务保存定时命令；到期时刻不是下次发送重试时间。 */
  public void insert(
      String eventId, String orderId, String topic, String eventType, String payload,
      Long deliverAtEpochMillis) {
    mapper.insertSql(
        parameters(
            "id", eventId, "orderId", orderId, "topic", topic, "type", eventType, "payload",
            payload, "deliverAt", deliverAtEpochMillis));
  }

  /** 查找已到重试时间的消息，或发送进程退出后租约已经到期的消息；查到不等于抢占成功。 */
  public List<OrderMessageDO> findReady(int limit) {
    return mapper.findReadySql(parameters("limit", limit)).stream()
        .map(
            row ->
                new OrderMessageDO(
                    RowValues.string(row, "event_id"),
                    RowValues.string(row, "topic"),
                    RowValues.string(row, "event_type"),
                    RowValues.string(row, "payload"),
                    RowValues.intValue(row, "attempts"),
                    row.get("deliver_at_epoch_ms") == null
                        ? null : RowValues.longValue(row, "deliver_at_epoch_ms")))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
  }

  /** 原子抢占一条消息；令牌随本次尝试生成，只有持有它的发送者才能更新结果。 */
  public boolean claim(String eventId, String token, LocalDateTime lockedUntil) {
    return mapper.claimSql(parameters("id", eventId, "token", token, "until", lockedUntil)) == 1;
  }

  /** Broker 确认发送成功后标记完成；旧租约的发送者不能覆盖新发送者的状态。 */
  public boolean markSent(String eventId, String token, String brokerMessageId) {
    return mapper.markSentSql(
            parameters("id", eventId, "token", token, "messageId", brokerMessageId))
        == 1;
  }

  /** 发送失败时恢复为待发送并设置下次时间；消息本身不会删除，也不会重新生成事件编号。 */
  public void scheduleRetry(String eventId, String token, LocalDateTime nextAttempt, String error) {
    mapper.scheduleRetrySql(
        parameters(
            "id",
            eventId,
            "token",
            token,
            "next",
            nextAttempt,
            "error",
            error == null ? "发送失败" : error.substring(0, Math.min(500, error.length()))));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
