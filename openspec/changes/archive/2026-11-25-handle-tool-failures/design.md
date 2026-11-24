# 设计

- CommerceToolCallback 区分 Schema 校验和业务调用阶段，返回 code/error/nextAction；业务拒绝、越权不是参数修正机会。
- 每轮 ToolExecutionContext 持有 ToolFailureBudget，以工具名称计数。换 toolCallId 不重置。参数原始调用后最多修正两次；同工具纯查询整轮最多增加两次执行，等待 200/400ms 加 0～99ms 抖动。
- ToolCallRecovery 位于同步/流式共用模型循环。仅明确只读工具可重试；load_skill、save_preference 虽是 READ 风险，仍有状态写入，不重放。
- 提议工具结果未知时核对会话中已有待确认卡，确认端沿用数据库 CAS 与原 actionId；下单仍复用原 trace。不会新建去重表、迁移或替代交易自身的幂等。
- 确认端先读业务执行回执，再决定异常回应。恢复响应 messageId=0、debug.recovered=true，不再次写聊天记录；异步 PROCESSING 仍不是建单成功。
- 明确业务拒绝用 BusinessRejectedException（继承原 IllegalStateException）标识，保持原 HTTP 异常契约。
- 边界：预算是每轮内存状态；进程重启不恢复预算。非下单操作未知时只保留 actionId 待核对，不自动续跑 APPROVED。取消/总超时立即终止本轮，交易不能据此宣称回滚。
