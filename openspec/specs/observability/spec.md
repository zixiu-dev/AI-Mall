# 运行指标与验证口径

## Purpose

约定运行时可查看的交易与缓存状态，以及查询、异步受理、成功建单和后置恢复的计量边界，保证压测结果具有场景、环境和原始证据，避免把接口快速响应或短时峰值当成完整业务链路的持续容量。

## 当前实现

| 指标 | 统计口径 |
|---|---|
| 商品查询 QPS | 每秒查询请求，配合 Caffeine / Redis 命中和数据库 loader 次数查看。 |
| 下单提交与受理 QPS | 分别统计提交到达和可靠受理；异步受理完成时可返回 `PROCESSING`。 |
| 建单 TPS | 每秒真实提交的建单事务，可按同步与异步区分。 |
| 消息与库存积压 | 分开观察建单处理中、最老等待、Broker 新增死信，以及库存待确认、待释放。 |

`/actuator/ordermonitor` 查看路由与积压，`/actuator/catalogcache` 查看缓存，显式开启后 `/actuator/throughput` 可读固定一秒桶。Prometheus 监测 Broker 建单消费组的死信与应用积压。排查单笔购买沿 trace、Outbox、Broker、订单事务和库存整理查，峰值数据记录机器、场景和窗口。

Agent 的后台回答通过 `GET /api/agent/messages/{messageId}/execution` 查询。先校验回答归属，再按助手消息 ID 合并 `agent_model_call` 和 `agent_turn_item`。前者每次模型请求一行，后者记录排队、工具执行、熔断跳过及恢复确认卡。诊断表通过有界队列异步写入，默认保留七天，并独立报告丢弃。整体模型耗时、熔断和 Token 仍通过 Prometheus 指标查看。 [单轮时间线](../../../docs/agent-design.md#单轮执行明细)

独立管理端的 `/agent-execution` 页面使用 `GET /agent-execution/api/turns?userId=...` 获取近期 30 轮并按会话分组，用 `GET /agent-execution/api/turns/{turnId}?userId=...` 查看具体一轮。执行树按推理步骤分组，工具用来源调用编号挂到对应模型下，尚未写入来源时仍显示在所属步骤下。页面刷新读取已提交的轮次和已落库的明细。 [后台页面入口](../../../src/main/java/com/bitmall/management/agent/AgentExecutionDashboardController.java)

模型尝试由模型网关采集，工具调用由工具执行器采集，整轮排队由聊天服务采集。模型和工具通过共同的步骤编号关联诊断事件，统一交给 `AgentExecutionJournal` 写入。 [Agent 调用边界](../../../docs/agent-design.md#agent-调用边界)

[统计流程图](../../../docs/images/measurement-pipeline.png) · [测试与压测](../../../docs/testing.md) · [一秒计数器](../../../src/main/java/com/bitmall/agent/common/monitor/OneSecondThroughput.java)

## Requirements

### Requirement: OBS-001 运行状态可观察

系统 SHALL 通过管理端提供缓存、下单、路由及基础健康/指标信息，并在独立配置的管理地址上暴露。

#### Scenario: 查看缓存与下单

- **GIVEN** 应用管理端已启用对应端点
- **WHEN** 读取 catalogcache / ordermonitor
- **THEN** 能够区分命中、回源、在途、积压及路由状态；未知不能伪装为零压力。

### Requirement: OBS-002 一秒吞吐按真实事件计数

系统 SHALL 在显式启用逐秒统计后使用完整固定 1000ms 桶，分别记录 HTTP 总响应/2xx、可靠受理和事务提交后的新订单。

#### Scenario: 异步返回处理中

- **GIVEN** 购买只是完成可靠受理
- **WHEN** HTTP 返回 PROCESSING
- **THEN** 可计成功受理/响应，但不计成功建单 TPS。

#### Scenario: 回滚与重复消费

- **GIVEN** 事务回滚或消息仅返回既有订单
- **WHEN** 进行逐秒统计
- **THEN** 不得新增一次成功建单计数。

#### Scenario: 未开启秒桶

- **WHEN** 应用使用默认配置运行
- **THEN** 不宣称 throughput 端点已经装配并暴露。

### Requirement: OBS-003 性能结论与积压边界

系统 SHALL 在报告中同时说明环境、工作集、连接/到达模式、延迟、错误率、采样区间和恢复积压，不把不同口径或版本的结果直接混用。

#### Scenario: 只测到一秒峰值

- **WHEN** 编写容量或面试指标
- **THEN** 明确是一秒观测结果，不宣称已证明生产持续容量。

#### Scenario: 订单成功但后置积压

- **GIVEN** 成功建单之后仍有确认或发送任务
- **WHEN** 汇报本轮结果
- **THEN** 分别记录后置积压，不能把建单成功说成整条链路已追平。

### Requirement: OBS-004 异步建单与库存整理积压告警

系统 SHALL 分别暴露异步待建单、待确认和待释放的数量与最老等待时间，以及最后一次成功采样的时间。告警 SHALL 标明采样失效，并在数据有效时对持续等待告警。

#### Scenario: 建单与库存整理分别计量

- **GIVEN** 数据库包含同步购买、异步待建单和异步库存整理任务
- **WHEN** 后台采样
- **THEN** 只聚合对应的异步任务，独立展示三个阶段的数量及年龄。

#### Scenario: 数据库采样故障

- **GIVEN** 最近一次成功的采样之后数据库不可用
- **WHEN** 指标超过允许的新鲜度
- **THEN** 采样告警生效，陈旧积压数据不作为当前业务告警的依据。

### Requirement: OBS-005 Agent 单轮执行时间线

系统 SHALL 为商城后台回答按助手消息编号记录实际模型尝试与工具执行项，并按同一轮的发生顺序提供归属校验后的明细查询。诊断明细 SHALL 使用有界异步队列写入，默认保留七天，不影响聊天和交易结果。

#### Scenario: 一步推理切换模型

- **WHEN** 主模型调用失败并在同一步切换备用
- **THEN** 两次请求各记录一条，共用推理步骤编号，分别记录结果、耗时及上游提供的用量；熔断跳过只记录事件。

#### Scenario: 工具重试与确认卡

- **WHEN** 模型返回工具请求
- **THEN** 一个工具请求记录一个执行项，计实际业务分发次数，格式或权限校验前拒绝计零次；产生或找到确认卡时关联原编号。

#### Scenario: 幂等与归属查询

- **WHEN** 重试提交或重连原助手消息，或者查询一轮执行明细
- **THEN** 原轮次不重执行；查询先检查用户归属，只返回对应轮次的有限字段，不复制提示词及工具载荷。

#### Scenario: 诊断写入不可用

- **WHEN** 队列满、持久化失败或保留期限到期
- **THEN** 分别可观测写入丢弃或删除过期明细；诊断结果允许延迟或缺失，原聊天、确认和交易状态仍由原业务数据决定。

### Requirement: OBS-006 管理端查看 Agent 执行记录

系统 SHALL 在独立管理 Web 端口提供只读的 Agent 执行记录页面，支持按用户查看近期轮次及按助手消息编号定位。页面 SHALL 展示已落库的执行事件、推理步骤、状态、耗时及可用的模型用量，并区分处理中和暂无明细。

#### Scenario: 在管理端排查一次回答

- **GIVEN** 已启动独立管理端口，指定用户有后台回答
- **WHEN** 操作人员选择近期轮次或输入该回答的编号
- **THEN** 页面按记录顺序显示该轮的排队、模型尝试和工具等已有事件；刷新只重新查询。
- **AND** 列表和明细接口仅返回指定用户的轮次，诊断页面和新增接口仅在独立管理端提供。

#### Scenario: 明细异步写入或到期

- **WHEN** 查询时明细尚未写入或已不存在
- **THEN** 页面显示回答状态与暂无明细，未知的 Token 或首段耗时不会显示为零。

### Requirement: OBS-007 Agent 执行树展示调用关系

管理端 SHALL 按会话归类近期轮次，并可展开轮次中的各推理步骤。模型尝试 SHALL 分别显示，且有明确来源的工具 SHALL 显示在其来源模型调用下。

#### Scenario: 多轮会话与主备切换

- **GIVEN** 同一会话存在多个后台轮次，且选中轮次的一步里主模型失败、备用模型成功并发起多次工具请求
- **WHEN** 展开会话和对应步骤
- **THEN** 各轮在同一会话下可独立查看，主备调用位于同一步，工具按原有来源编号挂在备用模型调用下。

#### Scenario: 事件没有步骤或来源明细暂缺

- **WHEN** 有独立轮次事件，或工具已经落库但来源模型明细暂缺
- **THEN** 独立事件显示在轮次顶层，工具仍出现在所属步骤下并标明来源暂缺。刷新后可根据新数据恢复父子关系。

## Current limitations

Prometheus 告警需接入部署环境的应用、Broker 指标和通知渠道。历史压测是各自代码版本的本机结果；新增每单定时消息后没有重新测出新的峰值容量。

## References

[设计与流程](../../../docs/testing.md) · [验证入口](../../../src/test/java/com/bitmall/agent/common/monitor/OneSecondThroughputTest.java)
