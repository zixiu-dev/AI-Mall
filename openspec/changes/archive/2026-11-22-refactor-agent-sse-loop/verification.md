# 实际验证

验证日期为 2026-11-22。Java 17，H2 demo 隔离库，脚本模型和本机 HTTP/SSE 协议替身。

## 执行结果

- `mvn -q -DskipTests compile` 通过。
- 回归命令包含 SpringAiStreamReaderTest、SpringAiIntegrationTest、AgentFlowIntegrationTest、OpenAITransportTest、CommerceModulesIntegrationTest、AgentStateMigrationTest、PlainJavaModelsTest、LayeredArchitectureTest、ToolRegistryTest、ControllerBoundaryIntegrationTest，55 个测试通过。
- 新增取消后恢复测试 `SpringAiIntegrationTest#cancelledStreamStopsBeforeToolAndRepairsHistoryOnNextRound` 单独执行通过。工具开始前取消后无 tool_end/done，下一轮取得会话执行权并补中断回执，无自动重放。
- SpringAiStreamReaderTest 的 4 项覆盖生成结束前收到文本、取消上游、模型等待超时、忽略中断的工具保留门禁、排队取消与总预算。
- MockMvc 验证 start/delta/done 命名 SSE，以及占满 16 个线程和 32 个等待任务后的 error 提示。
- OpenAITransportTest 用本机 HTTP/SSE 端点发送拆分 JSON 工具参数，验证适配层合并参数、工具结果配对、真实文本增量和客户端重复使用。
- OpenSpec 严格检查 20 项通过，包含本次活动 change。归档后基线及其余活动 change 共 19 项严格检查通过。
- `git diff --check` 通过。

## 环境与边界

首次沙箱执行中，本机端口绑定与现有 Mockito JVM 附加权限被拒绝；随后在具备相应权限的本机测试进程中回归通过。没有调用真实云模型，没有用这些测试宣称模型回答质量或生产吞吐。模型 SDK 保留自身响应式 HTTP 实现，业务 Controller、Service、Agent 循环使用同步方法和事件回调。

业务或 JDBC 调用忽略线程中断时，会话执行权等待调用实际退出；数据库结果继续按持久化状态查询。SSE 网络断开在容器回调或发送失败被观察到后取消本轮。
