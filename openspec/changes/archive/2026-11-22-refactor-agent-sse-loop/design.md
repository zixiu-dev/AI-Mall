# 同步循环与流式输出设计

## Decisions

- Controller 创建 SseEmitter，向独立的 agentChatExecutor 提交一轮同步任务。线程池 core=4、max=16、queue=32，拒绝时发送 error。
- AgentChatService 使用 try-with-resources 持有会话门禁，通过 Consumer<StreamEventVO> 发送事件。
- SpringAiResponder 用 for 循环依次推理、校验、执行工具和保存消息；真实文本片段由 Consumer<TextChunk> 回调传出。
- SpringAiStreamReader 是 SDK 边界，利用 MessageAggregator 合并分片工具参数，将 SDK 流转为可关闭的 Java Stream，按片段同步迭代。SDK 仍使用其自身的流式 HTTP 实现。
- AgentExecution 绑定工作线程，通过计时器和连接回调中断阻塞等待。模型单次 60 秒、工具单次 15 秒，均受配置中的总预算约束。排队计入总预算。
- 门禁始终由执行线程 finally 释放。外部取消仅发出中断，不提前宣布数据库或工具已经回滚。

## Compatibility

请求路径、start/delta/reset/tool_start/tool_end/done/error 事件及最终回复对象兼容现有页面。工具结果、技能元数据、确认卡和中断工具恢复均复用现有服务。

## Failure and Recovery

客户端断开或发送失败后取消本轮，关闭模型订阅并阻止后续工具；数据库驱动忽略中断时等当前调用实际结束。每次请求独立消息列表和执行上下文，取消计时器不得中断被线程池复用的线程。模型故障保留真实不可用提示与已存在的确认卡。

## Validation

脚本模型验证工具权限和确认流程，MockMvc 验证命名 SSE，模型 HTTP/SSE 替身验证分片参数与真实增量。专门验证模型未结束就产生文本、取消关闭上游、工具忽略中断时保留门禁、排队取消和总预算。
