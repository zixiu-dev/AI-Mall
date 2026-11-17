# 构建多查询 Agentic RAG 检索链路

## Why

当前按品牌/类别读取整篇政策、以 SQL LIKE 排序，无法提供分组覆盖、真实语义召回或受控的查询增强。依据用户提供的《Agentic-RAG架构设计与编码交接.md》建立可验证的搜索工具流水线，保留现有 AgentScope 和交易确认机制。

## What Changes

- search_knowledge 保留 query 和旧筛选，增加互斥的 queries 数组及分组证据、覆盖、运行模式、降级信息。
- 接通可信原问题、有限历史、请求级增强/结果缓存及共享截止时间；不增加所有请求必经 Planner，不修改业务工具串行分发。
- 新增离线切块、可替换真实 embedding 适配、持久化版本索引、小集合精确向量召回、DB_KEYWORD 与 RRF。
- 默认 KEYWORD；HYBRID 明确校验模型/版本/维度，依赖失效显式降级或报错，不冒充 BM25/ANN。
- 可选批量模型查询增强，拒绝越界改写；整块证据预算同时维护 groups、hits 与 coverage。

## Capabilities

### New Capabilities

- `knowledge-retrieval`：多查询、可信原意、检索版本、融合、预算、故障与证据契约。

### Modified Capabilities

- `customer-service`：扩展 SUPPORT-003 为兼容的分组检索；保留渠道、来源与只读边界。

## Impact

knowledge 模块、Agent 工具 Schema/参数校验/上下文/结果压缩、demo 与 HTTP 查询、配置、双数据库 V19、离线索引命令及设计文档。无新交易规则或退款权限。

## Scope Boundary

不迁移 Python、不新增独立业务子 Agent、GraphRAG、通用代码执行器或分布式向量平台。SQL LIKE 仅称 DB_KEYWORD，RRF 是排名融合而非答案正确率；真实 embedding 与模型联调缺凭据时如实保留验证缺口。
