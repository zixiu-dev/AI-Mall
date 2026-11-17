# PostgreSQL + pgvector 知识索引

## Why

用户要求引入 PostgreSQL 作为向量数据库。当前向量以 JSON 放在关系表，Java 读取全部向量计算余弦；需要真实的 vector 存储与数据库检索，保留已经验证的 AgentScope 与证据契约。

## What Changes

- 增加隔离的 PostgreSQL 连接池、MyBatis 映射与独立 Flyway 迁移，商城主数据源继续 MySQL/H2。
- HYBRID 默认使用 pgvector 的 vector(1024)、数据库余弦距离；同版 chunk 的关键词检索也在 PG 内完成，保留 RRF。
- 默认精确 SQL；可选 HNSW 与迭代扫描，过滤后不足按合法组数量复核并回退精确查询。
- 版本/块/发布指针在 PG 内原子提交；构建期间锁定主库源文档并复核哈希，不宣称跨数据库写事务。
- 旧 V19 保留，仅 LEGACY 明确兼容模式使用；不能连不上 PG 就静默运行 Java 向量扫描。
- 增加本机/容器配置、真实 PG 及模型验证、更新预览和文档。

## Capabilities

### Modified Capabilities

- `knowledge-retrieval`：RAG-003/004 的持久化与召回方式，新增独立向量数据库契约。

## Impact

knowledge DAO/config/检索与索引服务、PostgreSQL 驱动/Flyway、部署与测试。主 Agent 框架、交易库表与 MQ 不迁移。不增加 reranker。
