# 持久化确认与业务写操作

## Purpose

约定助手提出购买、取消、领券和售后等写操作时的用户决定与执行边界，将可展示提议、后端保存参数和真实业务结果关联起来，防止模型口头承诺、参数变化或重复点击直接产生未授权交易。

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

## Current limitations

批准到实际执行前进程崩溃的自动恢复尚未完成，不能宣称任意中断位置都可自动续跑。HTTP 用户身份当前是演示传参，没有真实登录认证。

## References

[设计与流程](../../../docs/agent-design.md) · [验证入口](../../../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java)
