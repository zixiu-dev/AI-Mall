# Flyway 执行目录

了解当前表结构请直接看 [docs/database](../../../../docs/database/README.md)，不用按版本阅读本目录。

| 目录 | 数据库 | 谁执行 |
|---|---|---|
| mysql | MySQL 主业务库 | 默认应用启动 Flyway |
| migration | H2 demo/测试 | demo profile 的 Flyway |
| pgvector | PostgreSQL 知识索引 | 知识索引适配器的独立 Flyway |

已应用迁移不可重写或删除。后续结构变化新增迁移；当前快照只用于阅读，不替代升级历史。商品、用户、订单和政策种子均为项目演示/人工整理资料，不是在线真实交易数据。
