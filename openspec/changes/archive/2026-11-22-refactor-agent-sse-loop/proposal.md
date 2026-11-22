# 同步 Agent 循环与 SseEmitter

## Why

聊天服务和模型执行使用多层 Flux/Mono 组合，同步聊天也要订阅再 block，难以沿调用栈理解工具执行和状态保存。用户要求改为普通 Java 循环及 SseEmitter。

## What Changes

- 模型推理、工具执行和修复回答使用同步循环，SSE 事件通过回调发送。
- SSE 使用有界线程池。线程池满时返回明确繁忙事件。
- 断连中断所属工作线程并关闭模型流；忽略中断的业务实际退出后才释放会话门禁。
- 保留 POST 路径、命名事件、真实文本增量、技能校验、确认卡与持久化消息。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `agent-runtime`：AGENT-005 明确有界执行、超时和取消语义。

## Impact

Controller、AgentChatService、SpringAiResponder 和模型流适配层；数据库结构与业务交易协议沿用现有契约。
