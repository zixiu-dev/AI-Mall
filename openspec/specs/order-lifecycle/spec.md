# 支付取消与十五分钟超时关闭

## Purpose

约定已建立订单在待支付、已支付和关闭之间的合法转换，明确十五分钟支付窗口如何通过可靠延时检查和扫描兜底执行，保证重复消息与支付竞争不会让已支付购买被取消或让库存与优惠券重复返还。

## 当前实现

| 入口 | 实际动作 |
|---|---|
| `POST /api/store/orders/{orderId}/demo-pay` | 显式启用模拟支付后，锁订单，核对状态及期限，改为 `PAID`、减少锁定库存并核销券。 |
| `POST /api/store/orders/{orderId}/cancel` | 锁当前用户订单，核对仍待支付，关单、返还数据库库存、释放券和登记 Redis 返还任务。 |
| RocketMQ 延时消息与数据库扫描 | 共用关单服务，锁单后再次检查数据库期限。 |

支付默认从实际建单起算 900 秒。定时消息保存在 Outbox，使用持久化的原截止时刻投递；迟到仍能关单，提前投递须重试。关单提交后 Lua 才增加 Redis 可售量，重复取消和重复消费不会多返。已付订单保留付款状态，售后另走申请流程。

[关单流程图](../../../docs/images/payment-timeout.png) · [生命周期服务](../../../src/main/java/com/bitmall/agent/order/service/OrderLifecycleService.java)

## Requirements

### Requirement: LIFE-001 新订单支付窗口

系统 SHALL 为新订单持久化支付截止时间，默认从真实建单时刻起 900 秒，可按配置调整；已有订单读取原期限，异步受理排队时间不消耗支付窗口。

#### Scenario: 默认支付窗口

- **GIVEN** 应用未覆盖支付有效期
- **WHEN** 同步或异步真正建立订单
- **THEN** 支付截止约为建单时间加 900 秒；此时只是待支付。

#### Scenario: 重试不延长窗口

- **GIVEN** 订单已有支付截止时间
- **WHEN** 重复提交购买或重发超时命令
- **THEN** 继续沿用原截止时刻，不重新增加 15 分钟。

### Requirement: LIFE-002 可靠定时关单检查

系统 SHALL 在建单事务中同时保存超时检查发送意图，提交后按原截止时刻定时投递；补发时若已过期则立即发送检查。

#### Scenario: 发送失败后补发

- **GIVEN** 订单已提交但定时消息首次发送失败
- **WHEN** 恢复发送
- **THEN** 使用原命令编号和截止时间，不永久丢失关单意图。

#### Scenario: 消息晚于截止时间发送

- **GIVEN** 待发送检查已经过期
- **WHEN** 转发器补发
- **THEN** 立即进入检查流程，不再等待完整支付窗口。

### Requirement: LIFE-003 到期复核与扫描兜底

系统 SHALL 在超时检查或扫描执行时重新锁订单，只关闭仍待支付且已到期的订单；已支付或已关闭直接结束，未到期消息不得提前关单。

#### Scenario: 未付款到期

- **GIVEN** 订单仍为 PENDING_PAYMENT
- **WHEN** 到期检查消费或兜底扫描执行
- **THEN** 关闭为 CLOSED，释放待支付占用、返可售库存与适用券，并持久化后续库存返还。

#### Scenario: 提前或伪造消息时间

- **GIVEN** 数据库订单尚未到期
- **WHEN** 收到超时检查命令
- **THEN** 不得提前关闭；提前消息应允许后续重试。

#### Scenario: 支付与超时竞争

- **GIVEN** 支付和超时检查同时进入
- **WHEN** 执行状态转换
- **THEN** 只有合法结果生效；已支付订单不得被超时检查误关。

#### Scenario: 消息链路不可用

- **GIVEN** 订单已到期但消息关闭、积压、失败或进入死信
- **WHEN** 数据库到期扫描执行
- **THEN** 仍可通过相同业务规则关闭，不能因没有消息就永久占库存。

### Requirement: LIFE-004 支付与取消的幂等副作用

系统 SHALL 仅允许有效期内待支付订单进入支付，仅允许待支付直接关闭；支付不再扣一次可售，重复支付/取消/超时不重复核销或返还。

#### Scenario: 重复取消

- **GIVEN** 待支付订单已手动取消
- **WHEN** 再次调用 POST /api/store/orders/{orderId}/cancel
- **THEN** 返回原关闭结果，不重复返库存和券。

#### Scenario: 模拟支付

- **GIVEN** 显式启用 demo-pay 入口且订单未到期
- **WHEN** 调用 POST /api/store/orders/{orderId}/demo-pay
- **THEN** 标记 PAID 并消除待支付占用；不发起真实扣款。

#### Scenario: 已支付订单申请取消

- **GIVEN** 订单已支付
- **WHEN** 尝试直接取消
- **THEN** 拒绝直接取消并引导售后，不直接增加可售库存。

## Current limitations

到期消息可能因 Broker 或消费者积压延迟，未承诺恰好第 900 秒处理完成。支付是显式启用的模拟入口，没有真实支付回调与扣款渠道。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/order/service/OrderPaymentTimeoutIntegrationTest.java)
