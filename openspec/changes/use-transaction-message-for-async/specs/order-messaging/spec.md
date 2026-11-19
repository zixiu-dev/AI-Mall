# 可靠消息发送与消费（差异）

## MODIFIED Requirements

### Requirement: MSG-001 本地事务与消息原子

系统 SHALL 保证建单意图 ORDER_BUILD 命令与本地 trace 状态在同一逻辑事务中，通过 **RocketMQ 事务消息机制** 实现，不使用独立 Outbox 表。

#### Scenario: 使用事务消息发送

- **GIVEN** 需要发送 ORDER_BUILD 命令
- **WHEN** 执行异步受理
- **THEN** 
  - 发送 RocketMQ prepare 消息
  - 执行本地事务（更新 trace）
  - 本地事务成功返回 COMMIT_MESSAGE
  - RocketMQ 将消息设为可消费

#### Scenario: 本地事务失败不发送

- **GIVEN** 执行本地事务
- **WHEN** 本地事务抛出异常
- **THEN** 
  - 返回 ROLLBACK_MESSAGE
  - RocketMQ 删除 prepare 消息
  - 消费者不会收到消息

## REMOVED Requirements

### Requirement: MSG-001 Outbox 模式（原版本）

**Reason:** 改用 RocketMQ 事务消息替代

**Migration:** 
- ORDER_BUILD 命令使用事务消息
- 订单事件（ORDER_CREATED/PAID/CANCELLED）保留 Outbox
