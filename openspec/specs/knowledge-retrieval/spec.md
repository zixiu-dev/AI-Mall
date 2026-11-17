# knowledge-retrieval Specification

## Purpose

规定现有 Agent 搜索工具的多知识子问题、可信原意约束、版本化向量索引、同范围关键词与语义召回、排名融合、预算/故障和完整证据契约。搜索返回可核对资料，由原有外层工具循环综合；不授予知识文档交易执行权限。

## Requirements

### Requirement: RAG-001 兼容的多子问题入口

系统 SHALL 保留必填 query 和旧单查询筛选，允许可选的 1～3 条有效去重 queries；queries 不与 brand/category/scope 同用，元素类型、长度、空白和数量均在执行层验证。

#### Scenario: 多品牌配对

- **WHEN** queries 为 Apple 退货、Nike 配送
- **THEN** 分别返回两组，不凭空增加 Apple 配送或 Nike 退货需求

#### Scenario: 旧 HTTP 显式筛选

- **WHEN** 只读 HTTP 使用旧 query 与品牌/类别筛选
- **THEN** 在调用方明确选择的范围内检索，保留原有冲突补问；Agent 的模型参数仍须与后端本轮完整原文核对

#### Scenario: 非法数组

- **WHEN** queries 含非字符串、空白、超长或过多有效子问题
- **THEN** 返回参数错误，不静默执行其中一部分

### Requirement: RAG-002 可信原意与请求级上下文

系统 SHALL 使用后端本轮原文及有限历史校验子问题和改写，保留品牌/渠道/类别/标识符约束，合法子集可检索，未知品牌不能通过删去而宣称全部覆盖；本轮缓存不得跨请求或用户共享。

#### Scenario: 合法子集与漏查

- **GIVEN** 原问题包含 Apple 与 Nike 政策
- **WHEN** 子问题仅要求 Apple 政策
- **THEN** 允许查询 Apple，同时明确原问题的 Nike 政策未覆盖

#### Scenario: 指代歧义

- **WHEN** 用户问它能退吗且历史没有唯一可追溯主体
- **THEN** 补问而非猜测品牌；有唯一明确主体则可补全并保留引用依据

### Requirement: RAG-003 原子发布的真实向量索引

系统 SHALL 离线切块、生成真实 embedding 并在配置的知识索引存储中持久化模型身份/版本/维度/文本哈希与索引版本；完整候选版本核验成功后才切换发布指针，失败保留原可用版本。

#### Scenario: 构建中断或文本变化

- **WHEN** embedding 失败、维度不符或构建期间源文档变化
- **THEN** 候选版本不可发布，不覆盖原发布版本，不在数据库事务中等待模型

### Requirement: RAG-004 同范围两路召回与排名融合

系统 SHALL 在相同品牌/类别/地区和有效内容版本内运行 DB_KEYWORD 和真实向量召回，按排名 RRF 融合并分组分配证据；PGVECTOR 后端在 PostgreSQL 内使用原生向量距离排序；可配置精确或 HNSW 近似查询，准确报告检索方式，不将 LIKE 称为 BM25。

#### Scenario: 语义候选与范围隔离

- **WHEN** 向量支路发现语义相近但非字面命中的候选
- **THEN** 合法候选可参与融合，范围外或失效版本候选不能进入最终证据

#### Scenario: 过滤后近似候选不足

- **WHEN** HNSW 在指定版本、品牌、类别和地区内返回少于所需合法候选
- **THEN** 在同一合法集合内执行精确查询兜底，不跨范围填充，不宣称 ANN 保证全量精确排名

### Requirement: RAG-005 可选且有界的查询增强

系统 SHALL 默认直接查询，可配置单次批量模型增强；保留原查询，每子问题至多三条去重表达，越界、解析失败或超时回退原查询并提示。

#### Scenario: 重写改变条件

- **WHEN** 增强添加品牌、地区、数字、否定条件或业务结论
- **THEN** 丢弃增强，不改变原过滤范围

### Requirement: RAG-006 预算与故障语义

系统 SHALL 使用有界内部并发和统一截止时间；单路可用时明确降级，两路失败返回错误；某组失败而其他有证据时保留成功组并报告 PARTIAL。

#### Scenario: 依赖故障

- **WHEN** 向量失败而关键词成功或两路均失败
- **THEN** 前者报告运行降级而不冒充完整混合检索，后者为错误而非 NO_MATCH

### Requirement: RAG-007 分组证据与覆盖一致

系统 SHALL 保留顶层四状态及兼容 hits，新增 groups、coverage、warnings 与实际检索模式；引用保留来源、适用条件、内容与索引身份，压缩只移除完整证据并同步覆盖缺口。

#### Scenario: 上下文裁剪

- **WHEN** 某组所有证据因总预算被移除
- **THEN** 该组标明未覆盖，顶层 hits 与各组保留证据一致，不截取半条政策冒充完整条件

### Requirement: RAG-008 搜索工具与外层业务循环分离

系统 SHALL 复用现有 ReAct 工具循环，RAG 内不调用其他业务工具或生成业务完成结论；价格/订单事实仍读共享业务服务，既有写工具确认门禁保持有效。

#### Scenario: 政策与订单混合任务

- **WHEN** 用户要求比较政策并查询本人订单
- **THEN** 知识与订单工具各返回真实结果，再由外层综合；政策检索不能创建售后申请

### Requirement: RAG-009 PostgreSQL 向量存储隔离

系统 SHALL 提供独立 PostgreSQL + pgvector 知识存储，使用原生向量列持久化并在数据库内计算相似度，主商城交易仍使用原数据源；知识库迁移与事务独立，PG 故障不能静默改用 Java 向量扫描或让交易访问 PG。

#### Scenario: 默认混合后端

- **WHEN** 使用 PGVECTOR 后端构建及查询 HYBRID 索引
- **THEN** 版本、chunk 与发布指针存于 PG，关键词与向量读取同一发布版本，JVM 不全量加载向量计算排序

#### Scenario: 无向量库的普通模式

- **WHEN** KEYWORD 模式运行且 PG 不可用
- **THEN** 普通商品、交易和主库关键词查询仍可运行；HYBRID 失败遵守显式降级/错误契约

## Current limitations

默认 KEYWORD；HYBRID 默认使用 PostgreSQL + pgvector，支持精确 SQL 和可选 HNSW，默认构建/元数据快照最多1000块，没有大库容量或ANN召回率保证。主库V19的JSON/Java扫描仅用于显式LEGACY兼容。固定vector(1024)，更换维度需新增PG迁移并重建。

查询增强默认关闭，cross-encoder 未接入；源文档规则识别有限、长文不能只凭命中段落作法律裁决。没有增量 embedding 复用或旧版本自动清理。真实模型小样本验证不代表普遍任务成功率。

## References

[检索设计](../../../docs/knowledge-design.md) · [验证记录](../../changes/archive/2026-11-17-build-agentic-rag-pipeline/verification.md) · [统一测试](../../../docs/testing.md#15-agentic-rag-检索流水线2026-11-17)
