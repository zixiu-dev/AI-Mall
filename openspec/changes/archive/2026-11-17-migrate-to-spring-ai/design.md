## Context

现有 AgentScope 深入模型、消息、技能中间件和状态序列化，不能只替换 Maven 名称。保留业务服务和 OpenSpec 安全约束。

## Decisions

1. 使用 Spring AI 2.0.2 BOM、spring-ai-openai、spring-ai-client-chat；手动配置模型以兼容现有 OPENAI_* 网关变量，不启用无关模型自动配置。
2. ChatClient 每步只推理一次，ChatClient 禁用默认工具 Advisor，模型不自动执行工具。应用编排动态 ToolCallback、持久化请求/结果、60 秒模型截止、15 秒工具截止、整轮 180 秒及次数预算。SSE 真流式传递，不以一次阻塞结果伪装流式。
3. 保存 Spring AI Message 的显式 JSON 编码，键 spring-ai-v1；不依赖框架私有序列化。旧状态不删除，首次从普通历史导入，不恢复旧工具调用。新状态未配对调用补中断结果，不自动重放。
4. 技能只来自可见工具结果的后端元数据；身份通过 ToolContext，参数 Schema 二次验证；写工具只创建持久化确认提议。
5. RAG 保留受控检索流水线、独立 PG/MyBatis、硬过滤与索引原子发布。Spring AI EmbeddingModel 负责模型协议，避免通用 PgVectorStore 隐式建表或改变索引/过滤契约。
6. 不叠加自动 ChatMemory/RAG Advisor：本项目需要完整工具配对、技能收回、后端确认回执和多问题硬过滤；这些逻辑明确放在服务中并记录原因。

## Verification

迁移原框架集成用例，补状态迁移/中断配对、真实 SSE 协议及 embedding 身份/顺序校验。执行全套本地回归、隔离 PG/MySQL 与已有真实模型验证；仅记录实际运行结果。

用户追加授权整体 Boot 升级：迁移 Jackson 3、Boot 4 模块包名和 MyBatis-Plus Boot 4 starter；RocketMQ 保持原 NameServer 协议并验证兼容。

## 数据库与文档整理

用户追加要求直接看到当前数据库形态。提供 docs/database 下的 MySQL/PG 纯结构快照，前者从随机空库重放迁移后导出，再在第二库核验；后者在独立 PG 库验证。正常初始化仍由 Flyway 自动执行，保留 V1～V19 和 PG V1 校验值，不设置危险的自动 baseline，不读取业务数据做样例。

Boot 4 依赖兼容：MyBatis-Plus 3.5.18 Boot 4 starter；Jackson 3 通过兼容 defaults 保持原 JSON；H2 固定 2.3.232；RocketMQ 2.3.6 starter 在真实本机 5.3.2 Broker 验证通过。前台/Agent/检索的业务服务未拆成另一套实现。
