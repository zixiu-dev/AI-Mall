# 可靠消息发送与消费

## Purpose

约定购买受理、订单变更及定时检查的可靠发送和消费语义，保证已提交的发送意图可以恢复重试、消息重复不会重复办理交易，并明确发送成功、消费完成和真实订单成功这几种不同事实。

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

## Current limitations

采用至少一次投递和业务幂等，没有端到端恰好一次承诺。当前生命周期事件的消费作用是审计，未接入真实履约；发送和整理积压需要独立监控。

## References

[设计与流程](../../../docs/messaging-recovery.md) · [验证入口](../../../src/test/java/com/bitmall/agent/order/service/OrderMessagingIntegrationTest.java)
