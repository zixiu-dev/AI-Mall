# 购买意图与同步异步提交

## Purpose

约定一次购买如何获得稳定编号、绑定不可变成交意图并返回可靠结果，使页面和助手共享同一交易入口，明确同步建单与异步受理的成功界线，也让重复点击和网络超时之后仍有原结果可查询。

## Requirements

### Requirement: ORDER-001 后端发号与首次绑定

系统 SHALL 通过 POST /api/store/order-tokens 仅生成 traceId；首次有效提交绑定用户和购买快照，兼容预绑定入口允许提前准备购买记录。

#### Scenario: 仅申请编号

- **WHEN** 调用发号接口
- **THEN** 获得 UUID，不创建订单、不扣库存，也不要求已经存在 trace 任务行。

#### Scenario: 首次提交

- **GIVEN** 后端发出的 trace 尚未绑定
- **WHEN** 提交购买参数
- **THEN** 完成归属和参数检查后绑定本次快照。

### Requirement: ORDER-002 同购买幂等且允许再次购买

系统 SHALL 对同用户同 trace 的重试返回原状态或原订单，拒绝同 trace 更改购买内容，并保证一个 trace 最多对应一个真实订单。

#### Scenario: 锁失效或重复请求

- **GIVEN** 一次购买已成功或仍在处理中
- **WHEN** 同 trace 再次提交相同内容
- **THEN** 不能建立第二个订单，返回原结果或当前处理状态。

#### Scenario: 再次合理购买

- **GIVEN** 之前已购买相同商品
- **WHEN** 用户以新 trace 发起同样购买
- **THEN** 按新购买处理，不因为参数哈希相同而永久拒绝。

#### Scenario: 篡改购买内容

- **GIVEN** trace 已绑定 SKU、数量、地址和优惠
- **WHEN** 以同 trace 换参数提交
- **THEN** 拒绝内容变化，不能沿用原确认执行新购买。

### Requirement: ORDER-003 可靠异步受理

系统 SHALL 在返回 PROCESSING 前持久化购买快照、预占意图及可靠受理结果，并确保后续建单命令具有可恢复的发送依据。

#### Scenario: 正常异步受理

- **GIVEN** 共享库存预占成功
- **WHEN** 提交异步购买
- **THEN** 返回 PROCESSING 和原 trace，不冒充已有成功订单。

#### Scenario: 受理提交后进程退出

- **GIVEN** 受理结果已经持久化
- **WHEN** 尚未发送建单消息时进程中断
- **THEN** 恢复后仍能发送同一购买的建单命令。

### Requirement: ORDER-004 实际建单成功界线

系统 SHALL 仅在订单、明细、库存、适用券、trace 成功结果及可靠消息同事务提交后报告 SUCCESS 和 orderId；同步和异步共用成交规则。

#### Scenario: 建单成功

- **WHEN** 同步事务或异步消费者完成建单提交
- **THEN** trace 为 SUCCESS 且有真实 orderId，订单为 PENDING_PAYMENT，不宣称付款成功。

#### Scenario: 交易写入失败

- **GIVEN** 建单任一步骤失败
- **WHEN** 事务回滚
- **THEN** 不能保留半个成功订单，也不能增加成功建单计数。

### Requirement: ORDER-005 原编号查询与兼容入口

系统 SHALL 在 GET /api/store/order-traces/{id} 核对用户归属后返回持久化结果；POST /api/store/orders 与正式提交入口共享同一规则。

#### Scenario: 响应超时但结果未知

- **GIVEN** 购买提交发生网络超时
- **WHEN** 使用原 trace 查询或重试
- **THEN** 返回原购买结果，不能要求立即更换 trace 再买。

#### Scenario: 尚未绑定的 UUID

- **GIVEN** 只申请编号而未绑定或提交
- **WHEN** 查询该 UUID
- **THEN** 不虚构已受理记录。

#### Scenario: 查询订单及能力

- **WHEN** 读取 GET /api/store/orders 或 /order-options
- **THEN** 分别返回用户最近订单，或下单/演示支付配置能力；后者不是远程组件探活。

## References

[设计与流程](../../../docs/order-design.md) · [验证入口](../../../src/test/java/com/bitmall/agent/order/service/OrderSubmissionIntegrationTest.java)
