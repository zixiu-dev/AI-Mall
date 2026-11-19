## Why

异步建单有超时和库存恢复机制，运维目前缺少建单组新增死信、用户等待过长以及库存整理滞后的告警。仅凭 MQ 的死信消息无法判断购买结果。

## What Changes

- 使用 RocketMQ Broker 自带的 Prometheus 指标告警，限定为异步建单消费组。
- 采集异步建单、预占确认和释放的积压数量与最老年龄，标记采样新鲜度。
- 提供 Dashboard 检查和恢复排查说明。业务恢复继续由 trace、Outbox 与库存任务负责。

## Capabilities

### Modified Capabilities

- `observability`：增加异步建单和库存恢复的可观测场景。
- `order-messaging`：约定死信告警与原购买的状态边界。
