## Why

后续学习和开发统一使用 Spring AI，移除 AgentScope 运行依赖，同时保留商城交易、受控工具和 Agentic RAG 契约。

## What Changes

- Spring AI 2.0.2 配合升级 Boot 4.1.2，ChatClient/ChatModel 驱动流式推理，ToolCallback 适配共享业务工具。
- 显式工具循环在每次推理前重新裁剪历史、检查技能与确认状态；禁止框架自动执行隐藏工具。
- EmbeddingModel 替换手写 embedding HTTP；可选改写使用 ChatClient。保留版本化 pgvector 原生混合检索与 RRF。
- 真实模式标识从 agentscope 变为 spring-ai，页面、配置说明同步。
- 使用新的 spring-ai-v1 状态键保存框架消息，旧 SDK 状态保留但不自动重放；旧会话通过页面历史、真实确认回执继续。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `agent-runtime`: 真实执行模式更名 spring-ai；保留 AGENT-002 至 AGENT-005。
- `agent-memory`: 增加框架迁移的恢复边界，保留消息配对与用户隔离。

## Impact

影响模型适配、工具适配、状态序列化、前端模式提示及测试。无交易表结构变更，无新数据库迁移。主业务服务、索引表和发布指针保持原职责。

用户追加授权整体 Boot 升级：迁移 Jackson 3、Boot 4 模块包名和 MyBatis-Plus Boot 4 starter；RocketMQ 保持原 NameServer 协议并验证兼容。

文档整理：增加 docs/database 当前结构快照和初始化入口；保留 Flyway 历史校验，日常阅读无需追溯演化。
