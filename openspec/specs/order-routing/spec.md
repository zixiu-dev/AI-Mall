# 下单压力路由

## Purpose

约定系统如何根据交易压力为新购买选择同步或异步路线，并在采样失效或积压超过可接受范围时停止新的受理，以避免只根据库存大小或浏览热度切换交易模式并破坏已经受理购买的处理路径。

## 当前实现

| 模式 | 当前选择 |
|---|---|
| `SYNC` | 请求线程直接执行建单。 |
| `ASYNC` | 使用可靠受理和 MQ 消费建单。探针尚未成功、失效或连续失败时暂停未受理的新购买。 |
| `AUTO` | 每秒采样提交速率、同步 P95、在途请求、连接池使用/等待、积压数量和最老等待。Redis 聚合实例数据，使用连续高低压采样与冷却时间切换。 |

默认连续高压 3 次可升级，低压 10 次并满足冷却条件才恢复同步。首次持久化预占意图时固定本次路线。`/actuator/ordermonitor` 的 `routing.accepting` 可观察受理状态。新异步单在预扣前与受理时检查实际订单 Topic 的最近发送探针。消息发送不可用时暂停新的异步受理，AUTO 高压下返回 503 保护数据库；已经受理的 trace 继续查询和恢复。消费者堆积通过积压单独判断。

[压力路由图](../../../docs/images/pressure-routing.png) · [路由器](../../../src/main/java/com/bitmall/agent/order/monitor/OrderPressureRouter.java) · [Broker 探针](../../../src/main/java/com/bitmall/agent/order/mq/BrokerHealthChecker.java)

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

系统 SHALL 在首次预占意图持久化时固定 trace 路线；采样无效/过期、异步不可用或积压过高时拒绝新受理，不能将未知当成低压力。尚未受理的异步购买 SHALL 依据最近一次 Broker 发送检查结果，检查失败或过期时允许原 trace 重试。

#### Scenario: 系统切回同步

- **GIVEN** 某 trace 已被可靠受理为 ASYNC
- **WHEN** 该购买重试且系统当前是 SYNC
- **THEN** 仍沿原路线处理，不重新走一条库存逻辑。

#### Scenario: 采样失败或积压超限

- **WHEN** 新购买要求选择 AUTO 路线
- **THEN** 返回繁忙或暂不可用，仍允许按原 trace 核对已受理结果。

#### Scenario: 订单 Topic 暂时不可发送

- **GIVEN** MQ 已启用，购买尚未受理且其路线为固定 ASYNC 或 AUTO 选择的 ASYNC，订单 Topic 的发送检查未通过
- **WHEN** 用户提交购买
- **THEN** 返回暂不可用，首次预占时不扣库存，不写入建单消息。Broker 恢复后，尚未到期且未结束的购买可用原 trace 重试。

#### Scenario: 受理后 Broker 故障

- **GIVEN** 购买已可靠受理且 Broker 此时不可用
- **WHEN** 用户重试提交或查询原 trace
- **THEN** 可读取原受理结果，发送与建单仍按原记录恢复。

## Current limitations

积压阈值采用采样软保护。异步探针测量订单 Topic 的生产端发送能力，消费处理能力由积压观测补充。同步预算作为配置起点，实际容量由压测确认。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/order/monitor/OrderAutoRoutingIntegrationTest.java)
