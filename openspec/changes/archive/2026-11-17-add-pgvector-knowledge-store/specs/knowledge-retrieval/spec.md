## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: RAG-009 PostgreSQL 向量存储隔离

系统 SHALL 提供独立 PostgreSQL + pgvector 知识存储，使用原生向量列持久化并在数据库内计算相似度，主商城交易仍使用原数据源；知识库迁移与事务独立，PG 故障不能静默改用 Java 向量扫描或让交易访问 PG。

#### Scenario: 默认混合后端

- **WHEN** 使用 PGVECTOR 后端构建及查询 HYBRID 索引
- **THEN** 版本、chunk 与发布指针存于 PG，关键词与向量读取同一发布版本，JVM 不全量加载向量计算排序

#### Scenario: 无向量库的普通模式

- **WHEN** KEYWORD 模式运行且 PG 不可用
- **THEN** 普通商品、交易和主库关键词查询仍可运行；HYBRID 失败遵守显式降级/错误契约
