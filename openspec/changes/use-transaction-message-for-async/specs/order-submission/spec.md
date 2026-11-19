# 购买意图与同步异步提交（差异）

## MODIFIED Requirements

### Requirement: SUBM-004 异步可靠受理

系统 SHALL 在压力模式或显式配置时接受购买意图，独立提交受理状态，通过 **RocketMQ 事务消息** 异步建单，允许重复消息幂等消费。

#### Scenario: 异步受理使用事务消息

- **GIVEN** 当前路由为 ASYNC
- **WHEN** 提交购买意图
- **THEN** 
  - 本地事务只更新 trace 状态为 PROCESSING
  - 不写入 order_message_outbox 表
  - RocketMQ 事务消息保证 trace 更新与消息发送一致性
  - 消息延迟 < 100ms

#### Scenario: 本地事务失败消息不发送

- **GIVEN** 异步提交
- **WHEN** 本地事务更新 trace 失败
- **THEN** 
  - 事务回滚
  - RocketMQ 收到 ROLLBACK，消息不投递
  - 返回失败

#### Scenario: 回查机制

- **GIVEN** 本地事务执行后网络中断
- **WHEN** RocketMQ 未收到 COMMIT/ROLLBACK 响应
- **THEN** 
  - RocketMQ 定时回查应用状态
  - 应用查询 trace 表判断事务结果
  - trace 存在且为 PROCESSING 返回 COMMIT
  - trace 不存在或非 PROCESSING 返回 ROLLBACK

## Current limitations

事务消息依赖 RocketMQ，不能切换到 Kafka 或 RabbitMQ。
