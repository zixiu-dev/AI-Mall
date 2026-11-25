# 实际验证

- Java 17 / Maven。下列后台 HTTP 测试使用本机真实 HTTP、H2 及可控模型，模型网关测试使用本机 OpenAI 兼容替身。
- `mvn -o -q -Dtest=AgentResumeHttpIntegrationTest,SpringAiIntegrationTest,ModelGatewayTransportTest,ToolCallRecoveryTest,AgentExecutionJournalTest,AgentFlowIntegrationTest,SpringAiStreamReaderTest,OpenAITransportTest,AgentActionRecoveryTest,ModelCircuitBreakerTest,LayeredArchitectureTest,PlainJavaModelsTest test`。74 项通过，0 失败或跳过。
- HTTP 覆盖重复提交和 SSE 重连沿用原助手消息，其他用户读取明细被拒绝，非法参数计 0 次实际分发，查询重试后同一工具项实际分发两次，工具关联来源模型。模型说明失败后恢复原确认卡，诊断记录与页面使用同一原编号。
- 本机模型接口注入截断流和 HTTP 503。两次主备请求分别记账，后续熔断跳过单独记事件，实际尝试编号连续。备用回传用量正确，失败调用 Token 留空。队列满和注入批写故障分别计入丢弃，后续批次仍可写入。
- MySQL 8.0.42 使用临时库。执行 `MySQLMigrationIntegrationTest#migrationsCreateForeignKeysAndRemoveLegacyActivityModel+executionDetailsBatchQueryAndRetentionWorkInMySql+resumableAnswerStoresQuestionAndUpdatesSameMessageInMySql`，3 项通过，0 失败或跳过。覆盖 V21 Flyway、两表批量写入、按轮排序、过滤和批量删除过期行、原助手消息保留。
- `python3 -B scripts/export-database-schema.py`。V1～V21 重放后导出 30 张表，在第二个隔离 MySQL 库重新导入，比对表结构通过。
- `git diff --check` 通过。OpenSpec 严格校验归档前 20 项通过，归档后 19 项通过，均为 0 失败。

本次未调用远端模型，也未做写入专项压测。MySQL 验证与本地模型故障模拟各有独立证据。
