## MODIFIED Requirements

### Requirement: STOCK-003 库存状态转换幂等

系统 SHALL 对同 SKU 同 trace 的预占、确认、释放与关单返还执行合法状态转换。Redis 以每 SKU 的可售量和单次预占记录执行这些操作，重复操作不能再次扣减或增加可售量。建单后的确认只更新预占状态。

#### Scenario: 重复预占与失败释放

- **GIVEN** 同一购买的库存操作被重复调用
- **WHEN** 执行重复 RESERVE 或 RELEASE
- **THEN** 同一阶段只改变一次可售量。

#### Scenario: 成功后确认失败

- **GIVEN** 订单事务已提交
- **WHEN** 确认预占失败并进入恢复
- **THEN** 保留订单成功，只补确认，不释放成功订单的库存。

#### Scenario: 关单重复返还

- **GIVEN** 订单关闭且返还任务重复执行
- **WHEN** 重试 RETURN
- **THEN** 只返还一次，已确认或尚未确认的本次预占都能按状态收敛。

#### Scenario: 接续旧库存池

- **GIVEN** 旧版本已停止且 Redis 保留可售量及原预占记录
- **WHEN** 新版本确认或返还原预占
- **THEN** 沿用原可售量与预占记录执行状态转换，无需读取或维护旧的 SKU 预占汇总键。
