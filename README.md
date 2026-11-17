# AI - Mall

一个 Java / Spring Boot 模块化单体项目：用户既可以在页面购买商品，也可以让 Agent 查询商品、试算价格、生成确认卡，再由业务服务完成交易。

**面试主线：商品查询怎样保护数据库；一次购买怎样只建一单；异步建单怎样处理失败；Agent 怎样可靠调用真实业务。**

## 整体架构图

[![智购整体架构：访问入口、Agent 编排、业务服务、核心设计、外部组件，以及商品查询和同步／异步下单链路](docs/images/system-architecture.png)](docs/images/system-architecture.svg)

左侧按职责分层，右侧串起三条关键链路。**页面与 Agent 共用业务 Service；同步与异步共用建单事务；已受理、建单成功、已支付是不同状态。** 点击图片可查看可缩放的 SVG，也可打开 [高清 PNG](docs/images/system-architecture.png)。

## 先看这四份

| 顺序 | 文档 | 要能讲清楚的问题 |
|---|---|---|
| 1 | [整体架构](docs/architecture.md) | 模块怎么分？Controller、Service、DAO 各做什么？数据放在哪里？ |
| 2 | [接口与功能流程](docs/api-flows.md) | 每个 HTTP 接口经过哪些步骤？购物车、券、结算、查询怎么设计？ |
| 3 | [下单设计](docs/order-design.md) | 同步/异步完整链路、trace、幂等、Lua、Outbox、库存和补偿 |
| 4 | [Agent 设计](docs/agent-design.md) | ReAct、工具、Skills、用户确认、记忆与 SSE 怎样串起来？ |

## 按设计点继续阅读

| 主题 | 文档 |
|---|---|
| 商品检索与缓存 | [SKU 条件、缓存读取、Bloom、空值、双检锁、随机 TTL、HotKey](docs/product-design.md) |
| 品牌知识检索 | [多子问题、版本化向量索引、双路召回、RRF、渠道与来源](docs/knowledge-design.md) |
| trace 与幂等 | [购买与重试、接口锁、数据库兜底、MQ 去重](docs/idempotency-design.md) |
| 消息与恢复 | [Outbox、消费结果、预扣补偿、关单返还](docs/messaging-recovery.md) |
| 自动切换与监控 | [压力采样、模式切换、队列与积压](docs/pressure-monitoring.md) |
| 当前数据库结构 | [MySQL / PG 完整 DDL 与初始化入口](docs/database/README.md) |
| 数据模型与事务 | [核心表、库存含义、状态、约束与 Mapper](docs/data-design.md) |
| 运行与验证 | [启动和排查](docs/deployment.md) · [测试与压测总报告](docs/testing.md) |
| 面试准备 | [讲述提纲、追问、指标表达与演示](docs/interview-guide.md) |

实现讲解与架构图在 [docs 阅读导航](docs/README.md)，测试说明和压测结果已统一合并。后续功能开发按 [OpenSpec 规格与变更流程](openspec/README.md)推进：当前行为、差异提案、设计、任务和验收都有记录。

## 一分钟介绍项目

> 我做的是一个带智能助手的商城。页面和 Agent 共用同一套业务 Service，模型负责理解意图和选择工具，价格、库存和订单状态由后端判断。
>
> 商品详情按“参数校验 → Caffeine → Redisson Bloom → Redis → RLock 与锁后双检 → 数据库”读取，命中直接返回；空值缓存和随机 TTL 继续保护回源。交易使用后端 trace 标识购买意图，AOP 分布式锁减少重复执行，数据库行锁、CAS 和唯一约束兜底。
>
> 同步和异步共用建单事务。异步在 Redis 预扣后持久化受理状态和 Outbox，再由 RocketMQ 消费者建单；失败和中断通过持久化状态幂等恢复。Agent 写操作先保存确认卡，用户同意后恢复后端参数，再调用这些业务服务。

## 项目实际具备什么

- 商品搜索/详情、购物车、领券、结算、建单、订单查询、取消和模拟支付。
- 建单后默认 15 分钟未支付，RocketMQ 定时消息触发关单、返库存和释放券，数据库扫描兜底。
- 按交易压力选择同步/异步；两种模式共用 SKU 库存池与数据库建单规则。
- Spring AI 工具调用、按需加载业务手册、持久化确认卡、会话历史和用户偏好。
- 按品牌与文档类别检索退换货、保修、配送等政策，提供官方来源，缺失资料时明确补问。
- 真实 MySQL / Redis / RocketMQ 联调，以及区分缓存命中、缓存构建、受理 QPS 和成功建单 TPS 的压测。

主技术栈：Java 17、Spring Boot 4.1.2、Spring AI 2.0.2、MyBatis-Plus 3.5.18、Jackson 3、Flyway、MySQL、PostgreSQL / pgvector、Redis / Redisson、Caffeine、RocketMQ。前端为 HTML / CSS / JavaScript。

## 最快体验

在 Java 17、Maven 和项目依赖已准备好的环境中运行；首次编译的 HotKey 依赖说明见 [启动文档](docs/deployment.md#2-首次编译与快速体验)：

```bash
ORDER_SUBMISSION_MODE=SYNC mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

打开 <http://localhost:8080>，依次体验：商品详情 → 购物车/立即购买 → 结算 → 下单 → 查询/取消；在助手中先查询并选定具体 SKU，再要求购买，观察试算和确认卡。

**上述命令显式使用 SYNC：demo 使用内存 H2、确定性助手，关闭交易 Redis 预扣；重启重置数据。** 真实模型、Redis 预扣与 MQ 链路使用普通配置，环境变量见 [.env.example](.env.example)，具体部署见 [启动与排查](docs/deployment.md)。

## 面试时需要明确的边界

- 这是单应用按业务分包，尚未拆成独立部署的微服务，也没有实现分库分表。
- 使用演示用户身份，未接入真实登录；支付是显式启用的模拟入口，售后没有接入真实退款渠道。
- 知识检索默认数据库关键词；已实现可配置 HYBRID 与查询增强，并完成百炼 embedding / DeepSeek 真实联调。向量存储已接 PostgreSQL + pgvector，支持数据库精确/HNSW 检索；当前未做大库容量评测，见 [RAG 设计](docs/knowledge-design.md)。
- Agent 会话互斥目前是单进程能力；订单具备的数据库幂等不能直接等同于 Agent 会话跨实例串行。
- 压测记录代表对应机器、参数和场景。数据库建单成功后可能仍有 Redis 整理或 Outbox 发送积压，不能把单秒峰值写成生产持续容量。

验证命令：`mvn test`。本次文档的源码入口和接口清单均对应当前代码；各项边界见对应主题文档。

商城前台已提供独立商品详情、购物车、结算、优惠券与订单页面，支持手机布局。页面设计与购买交互见 [商城前台设计](docs/storefront-design.md)。

当前商品目录已换为 32 款真实品牌商品（Apple、米家、Nike、IKEA、MUJI），规格、品牌图片及参考价格均保留来源与核对日期，见[商品来源清单](docs/storefront-design.md#真实商品与价格从哪里来)。库存与支付仍为本地演示数据。

当前数据库 DDL 与初始化入口见 [docs/database](docs/database/README.md)；Spring AI 用法及源码职责见 [Agent 设计第 9 节](docs/agent-design.md#9-spring-ai-2-的使用方式)。
