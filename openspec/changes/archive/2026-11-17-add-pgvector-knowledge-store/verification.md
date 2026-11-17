# PostgreSQL + pgvector 验证（2026-11-17）

## 实际结果

最终一次 `verify-pgvector.py --full --real-model` 在同时配置的隔离 MySQL/PG 库运行 **239 项，0 失败、0 错误、0 跳过**。包含 9 项真实 PG 组件测试、1 项真实连接失败降级及 3 项百炼/DeepSeek 模型测试；主库/H2、Redis、交易与 Agent 原回归也在同次运行中执行。

原文 `.runtime/pgvector/full-tests.log`，汇总 `full-tests-summary.json`。本轮随机 MySQL 和 PG 测试库均清理，预览数据库保留。此前12项 PG+模型定向结果在 `real-tests-summary.json`，不重复相加当唯一用例数。

| 要求 | 实际覆盖 |
|---|---|
| RAG-003 | 真实 PG V1、候选版本发布、模型失败/维度错误保留旧指针、重复块导致 PG 事务回滚；源文档停用使向量失效；原源变化/锁定回归继续通过 |
| RAG-004 | 原生 vector(1024) 列；已知正交/相反向量距离 0/1/2 及排序；同版本双路、品牌/类别过滤；512 块合成数据中只有16块属于合法组，HNSW 返回合法10块 |
| RAG-009 | 主库仍是 H2/MySQL；PG 的 knowledge schema 独立迁移；原主库 V19 chunk 表不被新构建写入；PG 快照不含向量数组；KEYWORD 不访问 PG；真实拒绝连接时显式关键词降级 |
| 保留的 RAG / 交易边界 | 原意/分组/缓存/RRF/预算/权限/确认测试通过；3项真实模型执行，不用替身声称语义验证 |

## 真实组件和模型

- 本机 PostgreSQL 17.11、pgvector 0.8.6（Homebrew），仓库内独立集群，只监听 localhost，未启动系统服务。
- HNSW 原生索引 `idx_pgvector_chunk_hnsw` 的可执行计划已验证，原文 `hnsw-plan.txt`。计划测试显式关闭顺序扫描以检查索引路径可用；不声称默认22块查询必走 HNSW，也不提供 ANN 召回率保证。
- HNSW 使用 strict_order 迭代扫描；不足 `min(topK,合法组数量)` 时同范围精确补齐，精确SQL先物化过滤范围。合成测试验证返回范围和数量，不把它称为大库质量/容量基准。
- embedding 为真实 `text-embedding-v4` / 1024维。语义样本相关余弦 0.739406、干扰段落 0.380286；真实 PG 混合召回带相同索引版本。
- 真实主模型 `deepseek-ai/DeepSeek-V4-Flash` 工具序列：`search_knowledge → search_knowledge → order_query`，约 16013ms。查询增强适配器约 1600ms；服务默认1.5秒预算可能回退，Agent验收默认关闭增强。SDK未统一持久化token usage，不编造完整用量。

## 最终 JAR / 预览

独立索引命令在新 PG knowledge schema 中调用百炼构建并发布22块，`embedding` 为原生 vector(1024)，元数据 JSONB；查询不加载全量向量到Java。

预览 `http://127.0.0.1:18082`，发布版本 `knowledge-e9aed650-58e3-48d5-b203-60caa59f5167`，EXACT 模式，保留原H2演示源与业务数据。POST 多问题返回 Apple/RETURNS 和 Nike/SHIPPING；真实Agent政策查询返回HTTPS来源，没有确认卡。记录 `preview.json / http-report.json / preview-index.log / preview.log`，只重启了本任务建立的预览。

## 边界与运维

默认总构建/元数据快照上限1000块；没有大库容量评测、增量向量复用、旧版本清理或cross-encoder。物理维度1024，改变维度需新PG迁移并重建。历史主库V19未改写/删除，只供显式LEGACY兼容，不静默回退Java向量扫描。

PG Flyway在knowledge schema独立执行，vector扩展位于public；预装扩展不需要跳过V1。源库事务只锁定与读取文档，PG自己提交发布，模型请求在两者事务外，不宣称跨库写原子性。

Docker Compose 已提供独立 knowledge profile，固定镜像0.8.2-pg17；本机没有Docker，本轮未实际启动容器。容器版本与本机实测0.8.6分开说明。链接、图片、CLI strict 在归档前后核对，CLI 不代替业务测试。

生产Java继续通过三层/普通类约束，没有引入Spring JDBC持久化或record。PG密码和模型Key只在本机私有配置中，端口5433和预览18082仅监听localhost。

已完成归档前strict校验，CLI将RAG-003/004修改与新增RAG-009同步到基线并归档。最后一项包含归档本身，在CLI完成后才勾选。

归档后校验：17个基线规格、7个归档change全部strict通过，活动变更为0；本地相对文件链接缺失0，凭据源码匹配0，预览首页HTTP 200。总架构图/RAG图已重新渲染，检查并修正文字宽度。
