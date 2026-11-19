# 共享库存预占与幂等整理

## Purpose

约定同步和异步共用的可售承诺、预占恢复和最终库存判定，确保同一购买重试或多次补偿不会重复改变库存，明确多规格部分预占失败、建单后确认失败和交易库存池丢失时如何处理。

## 当前实现

普通配置下，同步和异步一起消耗同一套 Redis 可售池。MySQL `stock` 是数据库可售，`locked_stock` 是待支付占用。Redis 可能提前扣掉在途购买，建单事务仍按 `stock >= quantity` 条件扣减 MySQL。

| 键 | 内容 |
|---|---|
| `inventory:v1:{skuId}:available` | String，SKU 当前可接单数量。 |
| `inventory:v1:{skuId}:reservation:{traceId}` | Hash，保存本次预扣数量和状态。 |

写 Lua 前先在独立事务持久化预占意图。单 SKU 脚本在同槽检查余额、扣可售、写 `RESERVED`；多 SKU 每项独立执行。建单成功后 `CONFIRM` 只推进状态。建单失败用 `RELEASE`，未支付关单用 `RETURN`，Lua 按旧状态决定是否加回。即使该 SKU 还没来得及预扣，失败释放也可留下关闭标记，防止迟到执行再扣。

Redis 整理失败由 SQL 的待确认、待释放及返还任务重试。库存池丢失会停止相应 SKU 接单，需人工核对；终态预占记录保留供迟到重试判断。跨 Redis 和 MySQL 的一致性依靠持久化进度、幂等与恢复逐步收敛。

[库存服务](../../../src/main/java/com/bitmall/agent/order/inventory/OrderInventoryService.java) · [预扣 Lua](../../../src/main/resources/lua/inventory_reserve.lua) · [确认与返还 Lua](../../../src/main/resources/lua/inventory_finish.lua)

## Requirements

### Requirement: STOCK-001 共享可售承诺

系统 SHALL 在普通预扣配置启用时，让同步和异步购买共用 SKU 预占池；最终建单仍须校验数据库并执行有库存条件的扣减。

#### Scenario: 同步不能消耗异步承诺

- **GIVEN** 某 SKU 已为异步购买预留库存
- **WHEN** 同步购买同 SKU
- **THEN** 继续受共享可售数约束，不能绕过预占池。

#### Scenario: 库存不足

- **WHEN** 并发购买总量超过可售库存
- **THEN** 超出部分被拒绝，数据库可售与待支付占用不得因建单成为负数。

### Requirement: STOCK-002 预占前保存恢复意图

系统 SHALL 在发生库存预占副作用前保存可恢复购买意图，并在多 SKU 部分成功后失败时按绑定快照幂等释放。

#### Scenario: 进程在预占中断

- **GIVEN** 已保存 RESERVING 并完成部分 SKU 预占
- **WHEN** 进程中断或购买失败
- **THEN** 恢复能够识别这次购买需要释放的 SKU，不遗失补偿依据。

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

### Requirement: STOCK-004 库存池丢失时保护在途购买

系统 SHALL 对已经初始化但预占池丢失的 SKU 停止相关新购买，不直接用数据库可售数覆盖可能仍有承诺的库存池。

#### Scenario: 交易库存数据丢失

- **GIVEN** 数据库标记池已初始化且存在在途可能
- **WHEN** 交易 Redis 对应池数据缺失
- **THEN** 拒绝继续盲目接单，要求核对在途任务与库存后恢复。

## Current limitations

多 SKU 预占不是一次跨所有 SKU 的原子操作；demo 显式关闭交易预扣。补货同步、池丢失后的自动重建和终态预占自动清理尚未实现。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/order/service/InventoryReservationIntegrationTest.java)
