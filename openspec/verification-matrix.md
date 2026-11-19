# 规格与验证入口

返回 [导航](README.md)。本表建立“要求在哪里、优先读哪个测试”的联系，**不代表每个 Scenario 都已具有独立自动化用例**。设计解释和已有测试存在于不同层次；实际覆盖范围需查看具体用例，后续变更在自己的 verification.md 逐项写出证据。

| 能力 | 稳定要求编号 | 主要验证入口 |
|---|---|---|
| [product-catalog](specs/product-catalog/spec.md) | CAT-001, CAT-002, CAT-003, CAT-004 | [CatalogSearchIntegrationTest.java](../src/test/java/com/bitmall/agent/product/controller/CatalogSearchIntegrationTest.java) |
| [catalog-cache](specs/catalog-cache/spec.md) | CACHE-001, CACHE-002, CACHE-003, CACHE-004, CACHE-005, CACHE-006 | [CatalogCacheIntegrationTest.java](../src/test/java/com/bitmall/agent/product/service/CatalogCacheIntegrationTest.java) |
| [shopping-cart](specs/shopping-cart/spec.md) | CART-001, CART-002, CART-003 | [CommerceModulesIntegrationTest.java](../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java) |
| [coupons](specs/coupons/spec.md) | COUPON-001, COUPON-002, COUPON-003 | [OrderSubmissionIntegrationTest.java](../src/test/java/com/bitmall/agent/order/service/OrderSubmissionIntegrationTest.java) |
| [checkout](specs/checkout/spec.md) | CHECKOUT-001, CHECKOUT-002, CHECKOUT-003 | [CommerceModulesIntegrationTest.java](../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java) |
| [order-submission](specs/order-submission/spec.md) | ORDER-001, ORDER-002, ORDER-003, ORDER-004, ORDER-005 | [OrderSubmissionIntegrationTest.java](../src/test/java/com/bitmall/agent/order/service/OrderSubmissionIntegrationTest.java) |
| [inventory-reservation](specs/inventory-reservation/spec.md) | STOCK-001, STOCK-002, STOCK-003, STOCK-004 | [库存预占集成测试](../src/test/java/com/bitmall/agent/order/service/InventoryReservationIntegrationTest.java)，覆盖缺少旧汇总键及旧预占数据接续 |
| [order-lifecycle](specs/order-lifecycle/spec.md) | LIFE-001, LIFE-002, LIFE-003, LIFE-004 | [OrderPaymentTimeoutIntegrationTest.java](../src/test/java/com/bitmall/agent/order/service/OrderPaymentTimeoutIntegrationTest.java) |
| [order-messaging](specs/order-messaging/spec.md) | MQ-001, MQ-002, MQ-003, MQ-004, MQ-005, MQ-006 | [消息集成测试](../src/test/java/com/bitmall/agent/order/service/OrderMessagingIntegrationTest.java) · [库存和死信恢复](../src/test/java/com/bitmall/agent/order/service/InventoryReservationIntegrationTest.java) · [真实死信与告警验证](../scripts/verify-order-build-dead-letter.py) |
| [order-routing](specs/order-routing/spec.md) | ROUTE-001, ROUTE-002, ROUTE-003 | [OrderAutoRoutingIntegrationTest.java](../src/test/java/com/bitmall/agent/order/monitor/OrderAutoRoutingIntegrationTest.java) |
| [agent-runtime](specs/agent-runtime/spec.md) | AGENT-001, AGENT-002, AGENT-003, AGENT-004, AGENT-005 | [SpringAiIntegrationTest.java](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java) |
| [agent-confirmation](specs/agent-confirmation/spec.md) | CONFIRM-001, CONFIRM-002, CONFIRM-003 | [AgentFlowIntegrationTest.java](../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java) |
| [agent-memory](specs/agent-memory/spec.md) | MEMORY-001, MEMORY-002, MEMORY-003, MEMORY-004 | [SpringAiIntegrationTest.java](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java) |
| [customer-service](specs/customer-service/spec.md) | SUPPORT-001, SUPPORT-002, SUPPORT-003, SUPPORT-004, SUPPORT-005 | [CommerceModulesIntegrationTest.java](../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java) |
| [storefront-experience](specs/storefront-experience/spec.md) | FRONT-001, FRONT-002, FRONT-003, FRONT-004, FRONT-005 | [verify-storefront.cjs](../scripts/verify-storefront.cjs)，实际范围见 [测试总文档第 12 节](../docs/testing.md#12-商城前台浏览器验收) |
| [knowledge-retrieval](specs/knowledge-retrieval/spec.md) | RAG-001～009 | [KnowledgePipelineIntegrationTest](../src/test/java/com/bitmall/agent/knowledge/KnowledgePipelineIntegrationTest.java)、[RealRagIntegrationTest](../src/test/java/com/bitmall/agent/knowledge/RealRagIntegrationTest.java) |
| [observability](specs/observability/spec.md) | OBS-001, OBS-002, OBS-003, OBS-004 | [一秒吞吐测试](../src/test/java/com/bitmall/agent/common/monitor/OneSecondThroughputTest.java) · [监控快照测试](../src/test/java/com/bitmall/agent/order/monitor/OrderBuildBacklogMonitorTest.java) · [告警规则测试](../deploy/monitoring/order-alerts.test.yml) |

## 跨组件补充入口

| 要核对的保证 | 入口 |
|---|---|
| MySQL 方言、迁移、外键、事务及消息精度 | [MySQLMigrationIntegrationTest](../src/test/java/com/bitmall/agent/integration/MySQLMigrationIntegrationTest.java) |
| 异步受理、真实消费入口与可靠建单 | [OrderAsyncSubmissionIntegrationTest](../src/test/java/com/bitmall/agent/order/service/OrderAsyncSubmissionIntegrationTest.java) |
| 控制器参数/归属边界 | [ControllerBoundaryIntegrationTest](../src/test/java/com/bitmall/agent/integration/ControllerBoundaryIntegrationTest.java) |
| 模型配置适配，不是模型效果评估 | [OpenAITransportTest](../src/test/java/com/bitmall/agent/agent/service/runtime/OpenAITransportTest.java) |
| 普通 Java 类与业务分层 | [模型检查](../src/test/java/com/bitmall/agent/architecture/PlainJavaModelsTest.java) / [分层检查](../src/test/java/com/bitmall/agent/architecture/LayeredArchitectureTest.java) |
| 真实 Broker 定时投递、同步/异步关闭与库存守恒 | [verify-payment-timeout.py](../scripts/verify-payment-timeout.py) |

## 当前已有证据与使用方式

2026-11-16 支付超时实现后的全量回归为 170 项通过、零失败、零跳过，包含独立 MySQL 与真实 Redis；真实 Broker 关单验证使用 12 秒测试窗口，正式默认 900 秒。详细记录在 [统一测试文档](../docs/testing.md)，真实运行原文在本机 [.runtime 报告](../.runtime/payment-timeout-tests/20261116-212709-b0e536/report.json)。

本次建立 OpenSpec 只进行规格结构、关联和归档文档校验，没有重新运行业务压测。不要把历史 170 项当作未来每次代码改动之后都自动通过；新变化要重做适当验证。克隆仓库不附带忽略提交的 .runtime 原始文件，应按测试文档复现。

新增或修改要求时，同步本表中的编号与入口。若用例尚缺，明确记录缺口，不能因为找到近似测试文件就宣布全部新场景已覆盖。

## 本次 OpenSpec 建立的实际检查

使用官方 CLI 2.0.0：15 个基线规格通过 `validate --all --strict --no-interactive`，已完成案例通过 `validate --archived --strict --no-interactive`。另外在临时副本构造关单变更前态，验证完整 delta、工作流状态、config 上下文和任务规则加载，再实际执行 archive；合并后的要求块与当前基线一致。这个演练不修改项目原基线，也不声称存在早于实现的历史 OpenSpec。

检查了文档本地链接、规格编号唯一性、每个要求的可触发/可断言场景、案例差异与当前要求的一致性。本次没有改变应用行为，因此没有重跑 Java 全量测试或发压。

## Redisson 变更后的补充证据

当前版本全量回归为 174 项、零失败/跳过；此前关单案例的 170 项是其当时范围。新缓存测试覆盖原生 RBloomFilter、锁前过滤、独立客户端互斥、看门狗、失锁回填保护和未提交登记恢复。另以两个独立 JVM 的 512 次请求验证 64 key 只回源 64 次，并复验真实 MQ 定时关单。统一证据与限制见 [测试总文档第 11 节](../docs/testing.md#11-redisson-统一与缓存顺序验证)。


真实目录补充：CAT-004 关联 [RealCatalogMigrationTest](../src/test/java/com/bitmall/agent/product/service/RealCatalogMigrationTest.java) 与 [RealCatalogIntegrationTest](../src/test/java/com/bitmall/agent/product/service/RealCatalogIntegrationTest.java)；FRONT-005 关联 [verify-real-catalog.cjs](../scripts/verify-real-catalog.cjs)。旧目录用例的显式夹具和实际验证边界见 [测试总文档第 13 节](../docs/testing.md#13-真实商品目录验证2026-11-17)。


品牌知识检索补充：SUPPORT-003/004/005 主要关联 [BrandPolicyIntegrationTest](../src/test/java/com/bitmall/agent/knowledge/BrandPolicyIntegrationTest.java)、[BrandPolicyMigrationTest](../src/test/java/com/bitmall/agent/knowledge/BrandPolicyMigrationTest.java)、[Spring AIIntegrationTest](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java) 和 [浏览器脚本](../scripts/verify-brand-policies.cjs)。本轮全量 200 项通过；品牌来源、缺失/冲突、渠道、只读隔离及安全引用的实际范围见 [测试总文档第 14 节](../docs/testing.md#14-品牌政策知识检索验证2026-11-17)。

Agentic RAG 补充：最终完整回归 229 项（226 通过，3 项真实模型默认跳过）；另外三个真实模型测试实际联网全部通过，包含百炼 embedding 和原 DeepSeek 工具循环。详见 [统一测试第 15 节](../docs/testing.md#15-agentic-rag-检索流水线2026-11-17) 与 [变更验证](changes/archive/2026-11-17-build-agentic-rag-pipeline/verification.md)。

PGVECTOR补充：RAG-003/004/009 关联 [PgVectorIndexIntegrationTest](../src/test/java/com/bitmall/agent/knowledge/PgVectorIndexIntegrationTest.java)、[PgVectorUnavailableIntegrationTest](../src/test/java/com/bitmall/agent/knowledge/PgVectorUnavailableIntegrationTest.java) 和3项真实模型测试。本轮完整239项全通过、零跳过，原文与边界见 [统一测试第16节](../docs/testing.md#16-postgresql-与-pgvector2026-11-17)。

Spring AI 迁移补充：AGENT-001～005 / MEMORY-002～004 由 [SpringAiIntegrationTest](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java)、[AgentStateMigrationTest](../src/test/java/com/bitmall/agent/agent/service/runtime/AgentStateMigrationTest.java)、[OpenAITransportTest](../src/test/java/com/bitmall/agent/agent/service/runtime/OpenAITransportTest.java) 验证；RAG 使用既有 PG、embedding 协议及单独真实模型用例。当前 242 个不同用例均通过（完整本地跑跳过的 3 个模型用例随后单独通过）；真实 MQ SYNC/ASYNC 各三笔与结构快照核验见 [测试总文档第 17 节](../docs/testing.md#17-spring-boot-4-与-spring-ai-2-迁移验证2026-11-17)。

CACHE-002 顺序调整补充：[CatalogCacheReadOrderTest](../src/test/java/com/bitmall/agent/product/cache/CatalogCacheReadOrderTest.java) 检查 Bloom 在共享缓存之前及明确否定短路；既有 CatalogCacheIntegrationTest 的本地命中、故障/误判与锁回归继续覆盖。31 项定向通过，见 [测试第 18 节](../docs/testing.md#18-商品详情先-bloom-后共享缓存2026-11-17)。

CACHE-006：商品稳定资料、批量回填与订单后局部失效见 CatalogCacheIntegrationTest；已预扣可售量和丢池保护见 InventoryReservationIntegrationTest。实测见 docs/testing.md 第 19 节。
