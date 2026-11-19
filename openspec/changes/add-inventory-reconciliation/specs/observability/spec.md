## ADDED Requirements

### Requirement: OBS-005 库存对账观测

系统 SHALL 记录库存对账批次、数量差异、证据缺失和待复核结果，并暴露相应运行指标。

#### Scenario: 对账日志与监控

- **WHEN** 对账执行或发现异常
- **THEN** 指标记录批次和异常类别，日志附 SKU、时间、数量和证据分类，供运维判断是否复查。
