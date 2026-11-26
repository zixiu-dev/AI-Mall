# 数据库结构

返回 [项目文档导航](../README.md) · [表之间的关系与事务](../data-design.md)

## MySQL 按业务查表

| 业务 | 当前 SQL | 主要数据 |
|---|---|---|
| 用户与地址 | [01-users.sql](mysql/01-users.sql) | 用户归属、收货地址 |
| 商品与 SKU | [02-catalog.sql](mysql/02-catalog.sql) | 商品主档、规格、库存和属性 |
| 购物车与优惠券 | [03-cart-coupons.sql](mysql/03-cart-coupons.sql) | 购物车项、券模板、用户券 |
| 下单与库存 | [04-orders.sql](mysql/04-orders.sql) | 购买 trace、订单、成交明细、库存返还任务 |
| 订单消息 | [05-order-messaging.sql](mysql/05-order-messaging.sql) | Outbox、生命周期消费回执 |
| 物流与售后 | [06-fulfillment.sql](mysql/06-fulfillment.sql) | 物流事件、售后申请 |
| Agent | [07-agent.sql](mysql/07-agent.sql) | 会话、消息、记忆、技能、确认卡和执行明细 |
| RAG | [08-knowledge.sql](mysql/08-knowledge.sql) | 政策源文档、MySQL 兼容索引 |

这八份脚本包含当前 30 张表的字段、中文注释、索引与约束。接手下单链路时先看 `04-orders.sql`，再用 `05-order-messaging.sql` 对照异步建单与关单消息。Agent 的 `conversation.id` 对应会话编号，后台助手的 `chat_message.id` 对应轮次编号。

[PostgreSQL/pgvector 索引](pgvector-current-schema.sql)单独维护，包含索引版本、发布指针、文本切片三张表。MySQL 中的 `knowledge_index_*` 是 LEGACY 兼容路径，默认向量召回走 PostgreSQL。详见 [RAG 设计](../knowledge-design.md)。

## 应用建库与升级

准备好空库后按[部署说明](../deployment.md)启动。Flyway 先执行带注释的基线，再插入种子数据。

| 数据源 | 建表基线 | 种子数据 |
|---|---|---|
| MySQL | [B22](../../src/main/resources/db/mysql/B22__schema.sql) | [V23](../../src/main/resources/db/mysql/V23__seed_data.sql) |
| H2 | [B22](../../src/main/resources/db/migration/B22__schema.sql) | [V23](../../src/main/resources/db/migration/V23__seed_data.sql) |
| PostgreSQL | [B2](../../src/main/resources/db/pgvector/B2__schema.sql) | [V3](../../src/main/resources/db/pgvector/V3__seed_data.sql) |

已有 Flyway 历史的库升级时，设置 `DB_HISTORY_DIR` 指向旧迁移目录。目录下按 `mysql/`、`migration/`、`pgvector/` 存放适用脚本，文件名和内容沿用旧迁移。本机默认读取 Git 忽略的 `.runtime/db-history`。新建空库从应用包内的两份脚本初始化。MySQL 可用 `SHOW FULL COLUMNS FROM order_submit_trace` 查看字段说明。

## 刷新 MySQL 快照

```bash
python3 scripts/export-database-schema.py
```

导出脚本在临时库执行当前 MySQL 基线和后续 SQL，按业务分组导出，再导入第二个临时库比对表、字段、约束和注释。脚本通过 `--mysql` 与 `--defaults-file` 支持本机私有连接配置。快照不含业务记录、连接信息和自增水位。新增表时在导出脚本的 `MODULES` 中登记它所属的模块。
