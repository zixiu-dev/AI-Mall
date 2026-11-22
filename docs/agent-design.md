# Agent 设计

聊天、确认和会话存储分别按职责查阅。

<a id="9-spring-ai-2-的使用方式"></a>

- [Agent 运行与工具调用](../openspec/specs/agent-runtime/spec.md)

- [用户确认与执行](../openspec/specs/agent-confirmation/spec.md)

<a id="旧会话怎么处理"></a>

- [会话历史与模型状态](../openspec/specs/agent-memory/spec.md)


## 同步执行与 SSE

`POST /api/agent/chat/stream` 由 Controller 创建 `SseEmitter`，把一轮聊天提交给独立的有界线程池。`AgentChatService` 在普通 Java 调用栈中持有会话执行权，最后通过 `finally` 释放。

1. 检查会话归属与待确认操作，发送 `start`。
2. `SpringAiResponder` 用 `for` 循环整理历史、校验技能并发送本轮工具定义。
3. `SpringAiStreamReader` 同步迭代模型文本片段，通过回调立即发送 `delta`。Spring AI 自身使用流式 HTTP，适配层负责关闭流和合并分片工具参数。
4. 模型请求工具时发送 `reset`、`tool_start`，在同一工作线程校验并执行工具，保存对应结果，再发送 `tool_end`，进入下一轮推理。
5. 完成回答后根据数据库中的确认卡修正文案，保存页面回复，发送 `done`，关闭连接。

同步 JSON 接口也调用同一个模型循环，只是不发送中间事件。循环的局部变量和上下文每轮创建，线程池复用不会共享用户消息。

### 执行预算与取消

SSE 工作线程池为核心 4、最多 16、等待队列 32，满时发送 `error` 提示稍后重试。排队计入 `app.agent.request-timeout-seconds`，默认总预算 180 秒。每次模型推理最多 60 秒，每次工具最多 15 秒，均受本轮剩余预算约束。

Controller 检测到发送失败、容器超时或连接结束后，用 `AgentExecution.cancel()` 中断工作线程。模型流在 try-with-resources 中关闭，后续推理和工具在执行前检查取消。如果当前数据库调用暂时忽略中断，会话执行权保留到它真正退出；持久化确认卡和交易结果仍按业务服务核对。没有配对结果的工具调用继续沿用下一轮恢复机制，补中断回执并由用户决定后续动作。

[同步模型循环](../src/main/java/com/bitmall/agent/agent/service/runtime/SpringAiResponder.java) · [SDK 流式适配](../src/main/java/com/bitmall/agent/agent/service/runtime/SpringAiStreamReader.java) · [请求预算](../src/main/java/com/bitmall/agent/agent/service/runtime/AgentExecution.java) · [SSE Controller](../src/main/java/com/bitmall/agent/agent/controller/AgentChatController.java)
