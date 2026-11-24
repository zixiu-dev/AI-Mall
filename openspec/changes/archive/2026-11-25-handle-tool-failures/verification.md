# 实际验证

## 条件与范围

本机 Java 17 / Maven；Agent 集成用可控 ChatModel、H2 数据库，网络/存储异常用故障注入；HTTP 重连测试使用本机真实 HTTP 连接。没有调用远端模型，也未制造真实 Redis/MQ/MySQL 宕机。本次不修改数据库结构或交易消息协议。

## 已运行

1. `mvn -q -Dtest=ToolCallRecoveryTest,AgentActionRecoveryTest,SpringAiIntegrationTest,AgentFlowIntegrationTest,AgentResumeHttpIntegrationTest,SpringAiStreamReaderTest test`：56 项通过，0 失败、0 跳过。
2. `mvn -q -Dtest=ToolCallRecoveryTest,AgentActionRecoveryTest,CommerceModulesIntegrationTest,OrderSubmissionIntegrationTest,OrderPaymentTimeoutIntegrationTest,LayeredArchitectureTest,PlainJavaModelsTest test`：54 项通过，0 失败、0 跳过；与第一组重叠 15 项，合计 95 个不同用例。
3. `git diff --check`：通过。
4. `npx --yes @fission-ai/openspec@2.0.0 validate --all --strict --no-interactive`：归档前 20 项通过；归档后 19 项通过（移除已归档活动变更），均 0 失败。

## 覆盖对应关系

- AGENT-007：参数三次错误封禁、参数修正成功、只读两次重试与抖动范围、耗尽后模型重复调用、写工具及状态型 READ 禁止重放、已有确认卡恢复、查询确认卡失败、业务归属拒绝、取消传播、阶段超时、技能权限拒绝。
- CONFIRM-004：重复 EXECUTED 确认读取原结果、业务提交后聊天保存失败、APPROVED 不重放、原 trace 查询失败仍保留 actionId。
- 真实模型循环使用脚本 ChatModel 连续发出错误参数，验证第四次即使参数正确也不会执行业务；HTTP 重复确认返回原结果且不增加订单。
- 原有确认竞态、下单、库存、超时关单、优惠券、售后及分层回归。

## 曾出现并已处理的失败

- 沙箱不允许 Mockito JVM 自附加与测试 HTTP 监听：改为获授权的本机测试运行，不算业务通过证据。
- 第一轮两处 Mockito 设置问题及取消已发货订单的业务异常未明确分类：修复后上述测试通过。
- 分层测试发现工具异常分类直接依赖 JDBC 异常类型：改为通用数据访问异常，重新通过。

## 仍存在的边界

- 参数格式由当前工具 Schema 的校验能力决定，未引入完整 JSON Schema 引擎。
- 模型是否遵从修正建议不由单元测试保证；后端次数限制独立执行。
- 底层调用若忽略中断，会等实际退出后才释放门禁；超时不是强制撤销业务。
- APPROVED 状态在执行前进程崩溃尚无自动续跑任务，保留原编号待核对。
- 恢复响应不补写缺失聊天消息，原业务回执保持在确认记录中。
