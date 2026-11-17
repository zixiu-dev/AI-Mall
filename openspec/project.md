# 项目背景与长期约定

基线日期：2026-11-16。本文描述当前项目背景和工程约束，行为契约见 [能力规格](specs/)，实现讲解与架构图见 [docs 导航](../docs/README.md)。

## 项目目标

让用户通过商城页面或自然语言助手完成选品、结算、购买、订单与售后申请。模型负责理解和选工具，后端业务服务决定可执行条件及真实结果。项目也用于面试，因此设计、失败边界和实测口径必须能解释、能核对。

## 技术与分层

- Java 17、Spring Boot 4.1.2、Spring AI 2.0.2、MyBatis-Plus 3.5.18、Jackson 3、Flyway；具体依赖看 [pom.xml](../pom.xml)。
- 一个应用进程；按 product / cart / coupon / checkout / order / agent / aftersales / knowledge / user 等业务模块划分。
- 模块内部 Controller 接 HTTP，Service 管理业务与事务，Repository / Mapper 访问数据库；dto/vo/entity/model 按职责使用。
- 普通 Java 类，不使用 record；允许按需使用 Lombok；中文说明类职责、关键方法和复杂步骤，不重复注释显然的语法。
- 前端为 HTML / CSS / JavaScript；REST 和 SSE 不产生两套业务规则。
- Redis 访问统一用 Redisson 原生对象或 RScript；MQ 消费去重保留旧适配。商品与交易使用独立客户端。RLock/RBloomFilter 不替代数据库事务。

## 权威数据与组件职责

| 组件 | 职责和边界 |
|---|---|
| MySQL | 商品/SKU、订单、券、trace、Outbox、确认卡、会话等业务事实与可恢复进度 |
| 商品缓存 Redis / Caffeine | 公共展示，按需缓存；失败时有界回源；不能决定最终成交 |
| 交易 Redis | 共享 SKU 预占、接口锁、消费标记和路由协调；预占池丢失需核对，不能盲目覆盖 |
| RocketMQ | 建单命令、到期关单检查、交易审计；至少一次投递配合幂等消费 |
| PostgreSQL + pgvector | 独立knowledge schema保存索引版本、正文块、JSONB、vector(1024)与发布指针，原生距离查询，不承载交易 |
| 模型服务 | 意图和工具选择；没有任意 SQL 或绕过确认的交易能力 |

## 统一术语

| 名称 | 含义 |
|---|---|
| productId / skuId | 商品款式 / 实际购买规格 |
| traceId | 后端生成的一次购买意图编号，不是链路追踪平台的 span，也不是参数哈希 |
| orderId | 真实已建订单 |
| eventId | 一条持久化消息的稳定身份，重发不换号 |
| actionId | 用户待确认的助手提议 |
| PROCESSING / SUCCESS | 已受理处理中 / 建单事务已提交，不等同支付状态 |
| stock / locked_stock | 数据库可售 / 待支付占用 |
| available / pending | Redis 可接单计数 / 尚待确认落库的预占 |

## 不得破坏的交易保证

1. 同 trace 不重复建单；新的合理购买使用新 trace；快照摘要用于发现同 trace 换内容。
2. Redis 锁只减并发重复，数据库行锁、CAS、唯一约束与事务共同兜底。
3. 普通配置的同步/异步共享预扣池；多 SKU 逐个预占，部分失败依靠持久化意图恢复。
4. 建单库存/券/订单/成功结果/消息同事务；跨 Redis/MQ 不宣称普通数据库事务自动原子回滚。
5. 新订单默认从真实建单起 900 秒待支付；定时消息与扫描共享锁单复核，已支付不得误关，重试不延长原期限。
6. 用户确认后恢复后端保存参数再执行业务；不能把模型文字或确认卡 EXECUTED 当最终付款事实。

## 当前边界与后续变化入口

| 当前边界 | 涉及规格 |
|---|---|
| 演示 userId，无真实登录；模拟支付，无真实退款渠道 | order-lifecycle / agent-confirmation / customer-service |
| 真实品牌与人工核对参考价；演示库存、运费与优惠规则，非实时品牌交易 | product-catalog / storefront-experience |
| 单体，无分库分表；知识支持 PostgreSQL/pgvector 精确与可选HNSW混合检索，已验证百炼与现有 DeepSeek 网关 | product-catalog / customer-service |
| Agent 会话门禁仅单进程；批准到执行前中断未全自动恢复 | agent-runtime / agent-confirmation |
| 有确认卡时阻断新聊天，不支持边问边修改提议 | agent-runtime / agent-confirmation |
| 终态预占自动清理、补货同步、池丢失自动重建未完成 | inventory-reservation |
| AUTO 是采样软阈值；异步就绪目前是功能开关 | order-routing |
| Outbox 和库存整理在高压后可能积压 | order-messaging / observability |

这些边界可成为后续 proposal 的问题来源，未获新功能要求前不自动实施。

## 验证与数据变更

- H2/demo 覆盖普通业务，真实 Redis 验证 Lua 和缓存，真实 MySQL 验证方言、约束与迁移；MQ 适配替身不等于真实 Broker 联调。
- MySQL 与 H2 的 Flyway 分别在 src/main/resources/db/mysql 和 db/migration，主库当前到 V19（保留旧索引兼容）；PG单独从V1迁移到knowledge schema；保留已应用脚本，结构变化新增迁移。
- 真实组件验证使用隔离库、隔离 Redis/MQ 和本轮命名空间，保留结果并清理本轮资源；不向共享开发 MQ 发压。
- 缓存查询、受理 QPS、成功建单 TPS、消息与库存整理积压分开统计；历史峰值不是生产 SLA。
- 本地凭据和大体积运行数据在忽略提交的 .runtime/；规格、设计和报告不写密钥。

运行方式、测试命令、实测证据统一在 [启动文档](../docs/deployment.md) 与 [测试总文档](../docs/testing.md)。规格场景关联测试入口见 [验证映射](verification-matrix.md)。
