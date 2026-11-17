# 数据库资料：先看当前结构

返回 [项目文档导航](../README.md) · [表的设计理由与事务](../data-design.md)

## 日常只看这些

| 文件 | 用途 |
|---|---|
| [mysql-current-schema.sql](mysql-current-schema.sql) | 当前 MySQL 全部表、索引、唯一约束和外键；没有 ALTER 历史，也没有种子数据 |
| [pgvector-current-schema.sql](pgvector-current-schema.sql) | 当前 PostgreSQL 知识索引的 3 张表、vector(1024)、JSONB 和 HNSW 索引 |
| [数据模型](../data-design.md) | 表为何存在、库存与状态关系、事务边界 |
| [启动说明](../deployment.md) | 凭据配置、创建空库、启动与索引构建 |

快照用于阅读和结构对照，**不是完整商城初始化脚本**：它们不包含技能、商品、政策、用户或 Flyway 历史。正常运行只需要创建空库并启动应用，由 Flyway 自动建表和导入项目种子数据，不需要自己逐份执行 V1～V19。

## 新环境与已有环境怎么处理

| 场景 | 正确做法 |
|---|---|
| 全新 MySQL | 按启动说明创建空数据库、配置连接并启动应用；Flyway 自动执行当前迁移 |
| 本地 demo/H2 | 启用 demo profile；应用自动创建临时库并迁移 |
| 新 PostgreSQL 知识库 | 准备独立数据库与 pgvector 扩展权限，运行离线知识索引构建；独立 Flyway 创建 knowledge schema 后生成真实向量 |
| 已有数据库更新代码 | 保留 flyway_schema_history；应用校验旧脚本并只执行尚未应用的新版本 |
| 只阅读数据库结构 | 看本目录两个 current-schema.sql，不需要读演化过程 |

**不要先导入 current-schema.sql 再启动默认 Flyway。** 这样会产生已有表却没有迁移历史的数据库；也不要用 baseline-on-migrate 或 repair 随意跳过验证。重置已有库、合并历史为新基线是独立的数据迁移操作，不能靠删除旧脚本完成。

## 历史脚本放在哪里

- [MySQL 迁移](../../src/main/resources/db/mysql)：V1～V19，主运行路径。
- [H2 迁移](../../src/main/resources/db/migration)：V1～V19，测试/demo 方言。
- [PostgreSQL 迁移](../../src/main/resources/db/pgvector)：独立 V1，不与商城主库混跑。

这些是应用的升级记录，保留在 resources 中给 Flyway 使用。它们不是面试阅读顺序；旧活动表在 V13 已删除，当前结构快照没有活动表。

`agent_scope_state` 保留历史表名，当前存的是 `spring-ai-v1` 版本的消息；主库的三个 knowledge_index_* 表是 LEGACY 兼容，默认 PGVECTOR 使用 PostgreSQL 的独立三表。保留旧表不表示默认链路同时往两套索引写数据。

## 更新快照

当前 MySQL 快照来自 **全新临时库重放 V1～V19**，再导入第二个临时库并比对结构，没有读取用户现有业务库。执行：

```bash
python3 scripts/export-database-schema.py
# 可通过 --mysql / --defaults-file 指定本机客户端及私有连接配置
```

脚本只使用随机命名的临时数据库，结束后清理；输出不包含业务记录、密码、主机连接信息或自增数据水位。新增迁移后重新导出并验证，不能手工修改快照来代替新增迁移。PG 快照按独立迁移维护，并在隔离 PG 库验证可执行性。
