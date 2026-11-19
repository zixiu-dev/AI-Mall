# 共享库存预占与幂等整理（差异）

## ADDED Requirements

### Requirement: STOCK-005 库存对账

系统 SHALL 定期对账 Redis 与 MySQL 库存数据，自动修正小差异，告警大差异，并清理孤儿预占记录。

#### Scenario: 库存数量对账

- **GIVEN** Redis 可售量与 MySQL 实际库存不一致
- **WHEN** 定时对账任务执行（每小时）
- **THEN** 
  - 差异 ≤ 5 件：自动修正 Redis 为 MySQL 值
  - 差异 > 5 件：记录告警日志，不自动修正
  - 所有对账操作记录到 `inventory_reconciliation_log`

#### Scenario: 孤儿预占清理

- **GIVEN** Redis 存在预占记录但对应 trace 不存在
- **WHEN** 定时对账任务执行
- **THEN** 
  - 删除 Redis 孤儿预占记录
  - 记录清理日志

#### Scenario: 终态预占清理

- **GIVEN** trace 已终态（SUCCESS/FAILED）且超过 7 天
- **WHEN** 定时对账任务执行
- **THEN** 
  - 删除对应 Redis 预占记录
  - 释放内存

#### Scenario: trace 状态补偿

- **GIVEN** trace 长时间停留在 CONFIRM_PENDING 或 RETURN_PENDING（> 5 分钟）
- **WHEN** 定时对账任务执行
- **THEN** 
  - 重新执行 CONFIRM 或 RETURN 操作
  - 更新 trace 状态

#### Scenario: Redis 库存丢失检测

- **GIVEN** SKU 已初始化但 Redis 可售量不存在
- **WHEN** 定时对账任务执行
- **THEN** 
  - 记录严重告警
  - 不自动重建（需人工介入）

## Current limitations

对账不保证实时一致性，允许短期差异，通过定时任务最终一致。
