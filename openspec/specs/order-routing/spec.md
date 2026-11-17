# 下单压力路由

## Purpose

约定系统如何根据交易压力为新购买选择同步或异步路线，并在采样失效或积压超过可接受范围时停止新的受理，以避免只根据库存大小或浏览热度切换交易模式并破坏已经受理购买的处理路径。

## Requirements

### Requirement: ROUTE-001 模式与压力依据

系统 SHALL 支持 SYNC、ASYNC、AUTO 配置，AUTO 基于交易请求、同步耗时、在途量、数据库连接压力与持久化积压选择新购买路线。

#### Scenario: 浏览热点不直接切路

- **GIVEN** 某商品访问被识别为热点
- **WHEN** 该商品出现新的购买
- **THEN** 不能仅凭浏览 HotKey 或库存多寡直接决定同步/异步。

### Requirement: ROUTE-002 迟滞与冷却

系统 SHALL 对持续高低压力使用不同恢复条件和冷却机制；压力采样与协调在后台执行，请求不逐次执行全量积压统计。

#### Scenario: 单次瞬时波动

- **GIVEN** 系统处于正常同步模式
- **WHEN** 仅一次压力升高随后恢复
- **THEN** 不因一次波动就在两种模式间反复切换。

#### Scenario: 持续高压与降压

- **GIVEN** 满足连续高压并经过切换约束
- **WHEN** 后续压力持续降低且冷却/恢复条件满足
- **THEN** 允许从同步升到异步，再恢复同步。

### Requirement: ROUTE-003 购买路线固定与拒绝保护

系统 SHALL 在首次预占意图持久化时固定 trace 路线；采样无效/过期、异步不可用或积压过高时拒绝新受理，不能将未知当成低压力。

#### Scenario: 系统切回同步

- **GIVEN** 某 trace 已被可靠受理为 ASYNC
- **WHEN** 该购买重试且系统当前是 SYNC
- **THEN** 仍沿原路线处理，不重新走一条库存逻辑。

#### Scenario: 采样失败或积压超限

- **WHEN** 新购买要求选择 AUTO 路线
- **THEN** 返回繁忙或暂不可用，仍允许按原 trace 核对已受理结果。

## Current limitations

当前积压阈值为采样软保护，不是精确全局限流器；异步就绪表示配置已启用，并非 Broker 实时探活。同步预算是配置起点，不是实测容量承诺。

## References

[设计与流程](../../../docs/pressure-monitoring.md) · [验证入口](../../../src/test/java/com/bitmall/agent/order/monitor/OrderAutoRoutingIntegrationTest.java)
