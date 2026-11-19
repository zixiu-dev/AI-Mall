# 功能 Spec

按业务流程查阅。每份 Spec 先写当前接口、实现步骤和设计点，后面附行为要求与验证入口。

| 功能 | Spec |
|---|---|
| 商品和前台 | [商品目录与 SKU](specs/product-catalog/spec.md) · [缓存与回源](specs/catalog-cache/spec.md) · [商城页面](specs/storefront-experience/spec.md) |
| 购买准备 | [购物车](specs/shopping-cart/spec.md) · [优惠券](specs/coupons/spec.md) · [结算试算](specs/checkout/spec.md) |
| 建单与库存 | [同步与异步下单](specs/order-submission/spec.md) · [压力路由](specs/order-routing/spec.md) · [Redis 预扣与恢复](specs/inventory-reservation/spec.md) |
| 消息与订单状态 | [Outbox、消费幂等与死信](specs/order-messaging/spec.md) · [支付、取消与超时关单](specs/order-lifecycle/spec.md) |
| Agent | [聊天与工具](specs/agent-runtime/spec.md) · [确认后执行业务](specs/agent-confirmation/spec.md) · [对话与记忆](specs/agent-memory/spec.md) |
| 客服 | [物流与售后](specs/customer-service/spec.md) · [品牌政策 RAG](specs/knowledge-retrieval/spec.md) |
| 监控 | [QPS、TPS 与积压](specs/observability/spec.md) |

复盘交易时，从商品查询读到结算和下单，再看库存、消息和关单。复盘 Agent 时，从聊天与工具读到用户确认，再看知识检索怎样提供证据。

[整体架构图](../README.md#整体架构图) · [当前数据库结构](../docs/database/README.md) · [运行与排查](../docs/deployment.md) · [测试与压测](../docs/testing.md) · [面试提纲](../docs/interview-guide.md)
