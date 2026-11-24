# 持久化确认与业务写操作

## Purpose

约定助手提出购买、取消、领券和售后等写操作时的用户决定与执行边界，将可展示提议、后端保存参数和真实业务结果关联起来，防止模型口头承诺、参数变化或重复点击直接产生未授权交易。

## 当前实现

1. 下单、领券、取消订单、提交售后的写工具在确认前生成并持久化 `pending_action`，保存用户、会话、规范化参数、摘要及幂等信息。下单另绑定后端购买 trace。
2. 用户调用 `POST /api/agent/actions/{actionId}/confirm`，只提交同意或拒绝。后端核对归属、用 CAS 保存决定，重复点击读取原结果。
3. 批准后从服务端记录还原参数，再调业务服务并保存实际回执。同步交易与确认卡执行结果一起提交。异步确认卡的 `EXECUTED` 代表工具已完成受理，最终订单仍查原 trace。

确认卡避免同一操作被重复批准或执行；购买 trace 负责同一购买最多建单一次。用户重新提议购买可生成新的两种编号。

[确认服务](../../../src/main/java/com/bitmall/agent/agent/service/action/AgentActionService.java) · [执行事务](../../../src/main/java/com/bitmall/agent/agent/service/action/ConfirmedActionExecutor.java) · [下单 Spec](../order-submission/spec.md)

## Requirements

### Requirement: CONFIRM-001 写工具先生成提议

系统 SHALL 将 coupon_claim、order_create、order_cancel、after_sales_submit 的模型请求先保存为待确认提议；未明确批准前不得执行对应业务变更。

#### Scenario: 要求帮忙购买

- **GIVEN** 助手已经选定 SKU 并试算
- **WHEN** 模型提出创建订单
- **THEN** 保存确认卡及后端参数，此时不能创建真实订单。

#### Scenario: 用户拒绝

- **GIVEN** 存在待确认提议
- **WHEN** 用户明确拒绝
- **THEN** 记录拒绝，不执行该业务写操作。

### Requirement: CONFIRM-002 确认只恢复后端参数

系统 SHALL 在 POST /api/agent/actions/{actionId}/confirm 核对归属和参数摘要，从持久化提议恢复执行内容，不接受确认时另换一套交易参数。

#### Scenario: 参数被篡改

- **GIVEN** 确认卡保存的摘要与参数不一致
- **WHEN** 尝试批准并执行
- **THEN** 拒绝不一致内容，不按篡改参数办理。

#### Scenario: 重复用户决定

- **GIVEN** 提议已经批准或拒绝
- **WHEN** 重复确认相同 actionId
- **THEN** 不能再次执行交易，也不能把原决定任意反转。

### Requirement: CONFIRM-003 执行标记与结果边界

系统 SHALL 将可加入同一数据库事务的业务执行与提议完成标记共同提交；异步购买提议执行完成只表示可靠受理已执行，最终订单结果仍查询 trace。

#### Scenario: 异步确认已执行

- **GIVEN** 用户批准的订单使用异步受理
- **WHEN** 操作标记 EXECUTED 且返回 PROCESSING
- **THEN** 提示处理中，不能直接展示已支付或必然建单成功。

#### Scenario: 已提交结果但回复丢失

- **GIVEN** 提议对应购买结果已经持久化
- **WHEN** 客户端重试或请求核对
- **THEN** 关联原 actionId / trace 读取状态，不另造一笔购买。

### Requirement: CONFIRM-004 重复确认与不确定结果恢复

系统 SHALL 在重复确认时读取原 actionId 的状态和回执，不重复执行业务；确认执行异常时先核对已持久化状态，不能把回复消息保存失败改写为业务失败。明确业务拒绝标记失败，其他结果未知保留原状态与编号。

#### Scenario: 成功后回复保存失败

- **WHEN** 业务与 EXECUTED 回执已经提交，但聊天记录写入失败
- **THEN** 返回原成功回执，不标记 FAILED，不再次执行业务。

#### Scenario: 重复确认尚在执行

- **WHEN** 原确认记录为 APPROVED，客户端再次确认
- **THEN** 返回处理中或待核对提示，下单按原 actionId 查询 trace，不再次调用执行器。

#### Scenario: 业务明确拒绝

- **WHEN** 执行时明确发现订单不可取消、优惠券不可领取等业务拒绝
- **THEN** 保存失败原因并告知用户，模型不能自动变更业务目标后重试。

## Current limitations

批准到实际执行前进程崩溃的自动恢复尚未完成，不能宣称任意中断位置都可自动续跑。HTTP 用户身份当前是演示传参，没有真实登录认证。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java)
