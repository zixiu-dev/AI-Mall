# 运行指标与验证口径（差异）

## ADDED Requirements

### Requirement: OBS-004 对账监控指标

系统 SHALL 暴露库存对账相关指标，记录对账执行情况、差异数量、修正次数和告警次数。

#### Scenario: 对账指标采集

- **WHEN** 对账任务执行
- **THEN** 记录以下指标：
  - `inventory_reconciliation_total`：对账执行次数
  - `inventory_reconciliation_diff_count`：发现差异数量
  - `inventory_reconciliation_auto_fix`：自动修正次数
  - `inventory_reconciliation_alert`：告警次数
  - `inventory_reconciliation_orphan_cleared`：清理孤儿预占数量

#### Scenario: 对账日志记录

- **WHEN** 发现差异或执行修正
- **THEN** 
  - 写入 `inventory_reconciliation_log` 表
  - 包含 SKU、时间、差异值、处理动作、详情
  - 保留至少 30 天

## MODIFIED Requirements

### Requirement: OBS-001 运行指标采集

在原有订单、库存、消息指标基础上，**增加对账相关指标**。
