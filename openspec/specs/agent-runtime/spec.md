# 助手会话与受控工具执行

## Purpose

约定助手如何处理用户输入、选择受控业务工具并返回有依据的结果，同时区分真实模型与确定性演示路径，保证工具调用不会绕过页面使用的交易服务，并使流式交互的中断与真实业务状态相互区分。

## 当前实现

| 接口 | 实际流程 |
|---|---|
| `POST /api/agent/chat` | 校验会话归属与执行门禁，保存输入，组装对话上下文，运行模型或演示规则，持久化回复。 |
| `POST /api/agent/chat/stream` | 共用聊天流程，通过 SSE 返回文本片段、工具进度与最终结果。 |
| `POST /api/agent/chat/turns` | 商城页面提交一轮后台回答，返回问题与助手消息编号。 |
| `GET /api/agent/chat/turns/{clientRequestId}` | 按请求编号找回已提交的回答。 |
| `GET /api/agent/messages/{messageId}` | 查询后台回答状态与最终正文。 |
| `GET /api/agent/messages/{messageId}/stream` | 按原消息编号订阅草稿快照、后续增量和终态。 |
| `GET /api/agent/status` | 返回当前配置使用模型或演示模式。 |

启用真实模型后，`SpringAiResponder` 用普通 `for` 循环组织 Spring AI 模型推理、工具执行、结果保存和下一次推理。`AgentChatController` 在有界工作线程中执行聊天，由 `SseEmitter` 发送事件。`SpringAiStreamReader` 将 SDK 流转换为可关闭的 Java Stream，实时读取文本并合并分片工具参数。工具目录保存参数 Schema、读写分类和所需 Skill；模型看到当前允许的工具，真正执行时再校验。Skills 是数据库中的操作手册，由 `load_skill` 读取。工具从服务端上下文取用户与会话，查询直连业务 Service，写操作交给[确认流程](../agent-confirmation/spec.md)。演示模式由确定性规则调用业务。模型单次 60 秒、工具单次 15 秒，均受本轮总预算限制；取消通过 `AgentExecution` 中断工作线程并关闭模型流，当前业务调用实际退出后释放会话门禁。兼容的 POST SSE 入口仍在连接断开时停止本轮。商城页面使用后台轮次，断连只移除旧订阅，终态按助手消息查询；交易结果按原 trace 查询。

[Agent 流程图](../../../docs/images/agent-execution.png) · [聊天服务](../../../src/main/java/com/bitmall/agent/agent/service/chat/AgentChatService.java) · [模型执行](../../../src/main/java/com/bitmall/agent/agent/service/runtime/SpringAiResponder.java) · [工具分发](../../../src/main/java/com/bitmall/agent/agent/service/tool/ToolDispatcher.java)

## Requirements

### Requirement: AGENT-001 真实模式与演示模式可区分

系统 SHALL 明确展示 spring-ai 或 demo 配置模式，真实模型失败时报告失败，不自动用规则助手冒充真实模型正常响应。

#### Scenario: 查询模式

- **WHEN** 读取 GET /api/agent/status
- **THEN** 返回配置模式；状态接口本身不证明远端模型此刻调用成功。

#### Scenario: 真实模型不可用

- **GIVEN** 应用启用真实模型
- **WHEN** 模型调用失败
- **THEN** 明确报告不可用或需要核对结果，不伪装为成功调用模型。

### Requirement: AGENT-002 受控工具与可信上下文

系统 SHALL 仅执行已注册且本轮允许的工具，检查参数格式、未知字段与必要技能；用户和会话信息由后端上下文传递，业务规则仍由共享服务判断。

#### Scenario: 猜测隐藏工具

- **GIVEN** 工具未因当前有效技能而开放
- **WHEN** 模型请求调用该工具
- **THEN** 拒绝执行，不能凭工具名绕过限制。

#### Scenario: 读取商品或订单

- **GIVEN** 查询工具允许执行
- **WHEN** 模型请求商品、试算或订单结果
- **THEN** 使用真实业务数据，不能把生成文本作为数据库事实。

### Requirement: AGENT-003 按需技能与上下文有效性

系统 SHALL 仅将成功加载且完整办理手册仍在可见工具历史中的技能视为有效，每次推理和实际执行均检查工具开放条件。

#### Scenario: 只在聊天提到技能

- **GIVEN** 用户普通文本提及某技能名
- **WHEN** 模型请求受该技能限制的工具
- **THEN** 不能把文本提及等同于加载成功。

#### Scenario: 手册已裁剪

- **GIVEN** 之前加载的完整手册不在当前可见历史
- **WHEN** 继续请求依赖该技能的工具
- **THEN** 重新限制该工具，不能沿用失去依据的授权。

### Requirement: AGENT-004 会话互斥与待确认拦截

系统 SHALL 在本进程对同会话执行实施互斥；已有待确认提议时，普通和流式聊天均优先返回该提议提醒，不创建新的交易提议。

#### Scenario: 存在待确认卡

- **GIVEN** 会话里已经有待确认操作
- **WHEN** 用户向 POST /api/agent/chat 或 /chat/stream 发送新请求
- **THEN** 返回原提议提醒，直到用户处理该提议。

### Requirement: AGENT-005 流式事件与中断边界

系统 SHALL 通过 POST /api/agent/chat/stream 提供真实文本增量、工具进度和完成事件。聊天在有界工作线程中执行同步循环，由 SseEmitter 推送命名事件；断开时停止本轮执行，但不能将连接中断等同于交易自动回滚。

#### Scenario: SSE 中断时业务可能已发生

- **GIVEN** 写操作已被批准且业务可能已经提交
- **WHEN** 客户端断开流连接
- **THEN** 取消模型流并阻止后续工具，按持久化操作和 trace 核对结果，不能宣称业务一定没有发生；当前业务忽略中断时，待实际退出后再释放会话门禁。

#### Scenario: 增量与工具顺序

- **WHEN** 模型逐段生成文本或请求业务工具
- **THEN** 通过 start、delta、reset、tool_start、tool_end 和 done 事件实时推送；分片工具参数完成聚合后才执行业务，最终回复按现有确认和持久化结果生成。

#### Scenario: 超时与执行容量

- **WHEN** 模型、工具或本轮请求超时，或者 SSE 工作线程及等待队列已满
- **THEN** 超时中断等待并按故障规则结束或返回工具错误，停止超出总预算的后续执行；容量满返回 error 提示稍后重试，不静默丢弃请求。

### Requirement: AGENT-006 后台回答与断线重连

系统 SHALL 按调用用户和请求编号幂等提交聊天轮次，在短事务中保存用户问题和助手占位消息。后台执行沿用会话互斥、工具与整轮预算。会话快照 SHALL 返回仍在生成的助手消息编号，回答结果和 SSE 订阅 SHALL 校验会话归属。

#### Scenario: 断开后订阅原回答

- **GIVEN** 助手消息仍在生成
- **WHEN** 原 SSE 连接断开，用户按原助手消息编号重新订阅
- **THEN** 原执行继续，新连接先收到当前草稿快照，再收到有序增量和工具事件。清空草稿的事件也影响后续快照。

#### Scenario: 刷新时已生成结束

- **WHEN** 用户查询或重新订阅已生成结束的助手消息
- **THEN** 返回已保存的完整答案或明确的失败提示，查询不再次执行模型或工具。

#### Scenario: 提交回执丢失

- **WHEN** 客户端使用原请求编号查询或重试提交
- **THEN** 查询返回原编号或明确表示尚未受理。重试复用相同的已提交问题和回答，同一编号更改问题或会话被拒绝。

#### Scenario: 超时、执行拒绝或进程重启

- **WHEN** 后台轮次失败、执行池拒绝或进程重启发现遗留占位
- **THEN** 将助手消息标记为失败，半截草稿不得作为成功回答，已产生的确认或购买状态继续按持久化业务记录读取。

### Requirement: AGENT-007 工具失败分类与有限恢复

系统 SHALL 将参数校验、访问拒绝、业务拒绝、临时故障和结果不确定分别返回结构化错误；同一聊天请求同一工具至多两次参数修正；纯查询至多自动重试两次，退避等待也计入总预算；副作用工具失败必须核对已有操作且禁止自动重放。模型再次调用不得重置预算。

#### Scenario: 参数连续错误

- **WHEN** 同一轮同一工具连续三次参数校验失败
- **THEN** 返回修正次数耗尽和最后错误原因，后续调用被阻止，用户可补充信息开启新一轮。

#### Scenario: 临时查询失败

- **WHEN** 纯查询遇到可识别的超时或连接故障
- **THEN** 最多按 200ms、400ms 加随机抖动退避重试两次，并遵守总截止时间和取消信号。

#### Scenario: 写操作结果未知

- **WHEN** 副作用工具超时或返回结果失败
- **THEN** 核对原操作记录，保留原编号并禁止盲目重放；确认重复请求只能读取原回执或处理中状态。

## Current limitations

会话门禁与生成草稿仅单进程，重启后结束遗留占位；已有确认卡会阻断只读追问和修改草稿，这属于当前交互限制，未来改善需明确修改 AGENT-004。真实模型任务质量不能用测试模型的框架测试直接证明。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java)
