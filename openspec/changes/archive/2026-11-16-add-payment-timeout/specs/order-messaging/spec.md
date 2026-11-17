# 消费职责变更

## MODIFIED Requirements

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
