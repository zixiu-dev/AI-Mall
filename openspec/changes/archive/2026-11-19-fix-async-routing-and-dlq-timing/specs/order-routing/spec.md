## MODIFIED Requirements

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
