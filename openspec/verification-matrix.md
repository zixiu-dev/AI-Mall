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
| [order-routing](specs/order-routing/spec.md) | ROUTE-001, ROUTE-002, ROUTE-003 | [压力窗口](../src/test/java/com/bitmall/agent/order/monitor/OrderPressureWindowTest.java) · [自动路由](../src/test/java/com/bitmall/agent/order/monitor/OrderAutoRoutingIntegrationTest.java) · [发送探针](../src/test/java/com/bitmall/agent/order/mq/BrokerHealthCheckerTest.java) · [固定异步受理与库存](../src/test/java/com/bitmall/agent/order/service/InventoryReservationIntegrationTest.java) · [真实断线和恢复](changes/archive/2026-11-19-fix-async-routing-and-dlq-timing/verification.md) |
| [agent-runtime](specs/agent-runtime/spec.md) | AGENT-001, AGENT-002, AGENT-003, AGENT-004, AGENT-005 | [工具与确认流程](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java) · [同步流、超时与取消](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiStreamReaderTest.java) · [HTTP SSE 与容量拒绝](../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java) |
| [agent-confirmation](specs/agent-confirmation/spec.md) | CONFIRM-001, CONFIRM-002, CONFIRM-003 | [AgentFlowIntegrationTest.java](../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java) |
| [agent-memory](specs/agent-memory/spec.md) | MEMORY-001, MEMORY-002, MEMORY-003, MEMORY-004, MEMORY-005 | [SpringAiIntegrationTest.java](../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java) · [会话恢复](../src/test/java/com/bitmall/agent/agent/controller/AgentFlowIntegrationTest.java) |
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

[测试、实测环境与结果](../docs/testing.md)集中记录各轮证据。

可读性重构的 clean 回归、兼容性对照和外部组件验证边界见 [2026-11-22 测试记录](../docs/testing.md#2026-11-22-可读性重构回归)。该轮保持既有行为要求。
