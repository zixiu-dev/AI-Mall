## ADDED Requirements

### Requirement: ORDER-006 事务消息路径的可靠异步受理

系统 SHALL 在灰度事务消息路径中保持已存在的异步交易约束。受理与提交证据一起保存，成功建单的判定沿用订单事务。

#### Scenario: 事务路径受理

- **GIVEN** 一笔购买满足受理条件且被选中使用事务消息
- **WHEN** 本地受理事务完成
- **THEN** 可靠提交后才允许返回 PROCESSING，响应不含虚构订单编号，重试沿用原路线和期限。

#### Scenario: 外层事务回滚

- **GIVEN** Agent 确认仍使用 Outbox
- **WHEN** 外层事务回滚
- **THEN** 本次受理和对应发送意图一起回滚。
