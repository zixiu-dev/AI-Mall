# 可靠消息发送与消费

## Purpose

约定购买受理、订单变更及定时检查的可靠发送和消费语义，保证已提交的发送意图可以恢复重试、消息重复不会重复办理交易，并明确发送成功、消费完成和真实订单成功这几种不同事实。

## 当前实现

| 消息标签 | 消费者职责 |
|---|---|
| `ORDER_BUILD` | 按 trace 读取快照并建单，正文只传 traceId。 |
| `ORDER_PAYMENT_TIMEOUT` | 到期锁订单查状态，按原截止时间处理关单。 |
| `ORDER_CREATED` / `ORDER_PAID` / `ORDER_CANCELLED` | 保存按 eventId 唯一的订单审计回执。 |

业务状态和 Outbox 在同一个 MySQL 事务内保存。提交后立即尝试发送，扫描负责补发。转发器用带持有者令牌的数据库租约抢任务；Broker 收到但本地没标 `SENT` 时，下次按原 eventId 和正文重试。订单状态查 trace 与订单表。

建单消费的 Redis String 键是 `idempotent:ORDER_BUILD:{traceId}`，值由 `PROCESSING:<ownerToken>` 变为 `DONE`。失败只能移除自己的标记；标记过期或 Redis 故障时，MySQL 行锁、状态和唯一关联仍防重复建单。明确业务拒绝在事务回滚后保存失败及释放进度，临时异常则交给 RocketMQ 重试。

### 死信处理

普通建单消费者最多重试 5 次。开启 `ROCKETMQ_BUILD_DLQ_ENABLED` 后，独立组订阅原建单组的死信 Topic。按 eventId 与 traceId 核对原 Outbox、锁住 trace，再判断：仍有效且符合重投条件则重发原消息；未建单且超时则标记失败并释放；已有终态只整理库存。格式或关联异常留日志，数据库故障继续重试。定时恢复负责兜底，Prometheus 从 Broker 指标观察新增死信。

[Outbox 与恢复图](../../../docs/images/outbox-recovery.png) · [转发服务](../../../src/main/java/com/bitmall/agent/order/service/OrderMessageRelayService.java) · [死信消费者](../../../src/main/java/com/bitmall/agent/order/mq/consumer/OrderBuildDeadLetterConsumer.java)

## Requirements

### Requirement: MQ-001 业务与发送意图共同提交

系统 SHALL 使建单命令与可靠受理同事务保存，使交易事件及支付超时命令与对应订单变更同事务保存；未提交的变更不能提前发送成已完成事实。

#### Scenario: 外层确认事务回滚

- **GIVEN** Agent 确认执行位于外层事务
- **WHEN** 后续处理导致外层事务回滚
- **THEN** 本次订单变更及其新消息共同回滚，不遗留已发生的假事件。

### Requirement: MQ-002 稳定身份与至少一次发送

系统 SHALL 在发送重试时复用同一消息身份和原文；仅在 Broker 确认成功后记录发送成功，异常与过期发送租约支持再次发送。

#### Scenario: 确认结果丢失

- **GIVEN** Broker 已收到而本地未记录发送完成
- **WHEN** 恢复扫描重发
- **THEN** 继续使用原 eventId；消费者能够承受重复消息。

#### Scenario: 发送者退出

- **GIVEN** 消息发送租约仍持有但进程已退出
- **WHEN** 租约到期
- **THEN** 其他发送者可以接手；旧发送者不能覆盖新租约的确认结果。

### Requirement: MQ-003 消费职责与业务依据

系统 SHALL 区分 ORDER_BUILD 建单、ORDER_PAYMENT_TIMEOUT 关单检查及 CREATED/PAID/CANCELLED 审计事件，各自只执行相应职责。

#### Scenario: 建单消息重复

- **GIVEN** 同 trace 已成功或明确失败
- **WHEN** 再次消费 ORDER_BUILD
- **THEN** 不再创建订单或再次扣库存。

#### Scenario: 审计事件重复

- **GIVEN** 同 eventId 已保存审计回执
- **WHEN** 再次收到生命周期事件
- **THEN** 至多保留一条该事件回执，不重复执行库存或履约。

### Requirement: MQ-004 消费失败与结果边界

系统 SHALL 将临时基础设施错误传播为可重试失败；明确业务拒绝在回滚交易后保存失败和释放进度，不以消息 ACK 或发送成功替代订单结果。

#### Scenario: 临时数据库错误

- **WHEN** 消费执行因数据库异常失败
- **THEN** 不得吞掉错误并误标业务成功，交由消息重试或持久化恢复。

#### Scenario: 明确业务拒绝

- **GIVEN** 后端核验发现报价或资格不成立
- **WHEN** 消费记录购买失败后结束
- **THEN** 允许消息处理完成，但 trace 不变成成功订单。

### Requirement: MQ-005 异步建单死信告警与业务恢复边界

系统 SHALL 支持按建单消费组监控 Broker 新增死信，并保留基于数据库购买状态的原有恢复流程。

#### Scenario: 建单消息耗尽消费重试

- **GIVEN** 监控已抓取 Broker 指标并配置了建单消费组
- **WHEN** Broker 将无法处理的建单消息写入死信队列
- **THEN** 监控能够发现新增死信，告警本身不更改订单、Outbox 或库存。

#### Scenario: 暂时失败与业务到期

- **GIVEN** 已受理的购买仍有库存预占且没有建立订单
- **WHEN** 发送或消费暂时失败
- **THEN** 购买仍有效时沿用原命令恢复，到期时由既有恢复流程判定失败并幂等释放预占。

### Requirement: MQ-006 异步建单死信消费

启用建单死信处理时，系统 SHALL 使用独立消费组订阅建单组的死信，在核对原建单事件和异步购买状态后执行原有事务恢复；定时恢复任务仍兜底。

#### Scenario: 已匹配的有效购买

- **GIVEN** 死信对应已受理且尚未达到处理截止时间的购买
- **WHEN** 消费死信
- **THEN** 按既有重投条件补发原 Outbox，沿用原 traceId、eventId 和截止时间。

#### Scenario: 已匹配的超时与终态购买

- **GIVEN** 死信对应已超时的待建单购买，或已有业务终态的购买
- **WHEN** 消费一次或重复消费
- **THEN** 未建单且已超时的购买写入超时结果并幂等释放；已有终态的购买只整理尚未完成的库存操作。

#### Scenario: 无法匹配的死信或依赖故障

- **GIVEN** 死信格式异常、无法匹配原购买和消息，或数据库暂时故障
- **WHEN** 消费死信
- **THEN** 格式或关联错误记录运维排查信息，数据库故障交由重试，无依据的消息不得触发交易或库存更新。

## Current limitations

采用至少一次投递和业务幂等，没有端到端恰好一次承诺。当前生命周期事件的消费作用是审计，未接入真实履约；发送和整理积压需要独立监控。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/order/service/OrderMessagingIntegrationTest.java)
