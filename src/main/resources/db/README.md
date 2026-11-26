# 数据源初始化 SQL

每个目录保留两个当前入口，一份带中文注释的建表定义，一份初始数据。

| 数据源 | 建表 | 初始数据 |
|---|---|---|
| MySQL | [B22__schema.sql](mysql/B22__schema.sql) | [V23__seed_data.sql](mysql/V23__seed_data.sql) |
| H2 | [B22__schema.sql](migration/B22__schema.sql) | [V23__seed_data.sql](migration/V23__seed_data.sql) |
| PostgreSQL / pgvector | [B2__schema.sql](pgvector/B2__schema.sql) | [V3__seed_data.sql](pgvector/V3__seed_data.sql) |

空库启动时，Flyway 先执行 `B` 基线，再执行种子脚本。已有库可通过 `DB_HISTORY_DIR` 加载源码外的旧迁移，默认使用本机 `.runtime/db-history`。

按业务查表见 [数据库结构](../../../../docs/database/README.md)。
