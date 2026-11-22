## MODIFIED Requirements

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
