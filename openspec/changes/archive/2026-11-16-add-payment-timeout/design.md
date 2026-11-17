# 定时关单：实现设计

> 追溯记录：对应已完成实现；问题与范围见 proposal。

## Context

同步与异步共用建单事务，已有持久化支付截止、订单行锁/CAS、关单返库存与券、Redis RETURN 任务和到期扫描。可靠发送采用 Outbox，因此延时命令也沿用这条链路。

## Goals / Non-Goals

新建订单默认十五分钟未付进入超时关闭；延迟检查、消息重投和支付竞争不能破坏订单及库存结果。存量订单和已有异步受理规则保持原语义；不引入真实支付渠道或新的跨库事务。

## Decisions

### 绝对时刻优于每次重发相对延迟

以实际订单的 payment_expires_at 计算一次 epoch 毫秒，和消息原文一起保存 deliver_at_epoch_ms。发送用 RocketMQTemplate.syncSendDeliverTimeMills；补发时已过期则即时发送。这样 Broker 或发送器故障不会让每次重发再等十五分钟。

传统默认延迟等级没有十五分钟档位，修改共享 Broker 等级或使用多次短延迟会增加部署与恢复复杂度。本实现使用 RocketMQ 5 时间轮，本机验证版本为 5.3.2。

### 创建订单与超时意图同事务

OrderCreationService 在库存、券、主表/明细、trace 成功及 CREATED 事件的同一事务内调用 enqueuePaymentTimeout；命令 ID 由 ORDER_PAYMENT_TIMEOUT + orderId 生成稳定 UUID。提交之后才唤醒发送，发失败由扫描重试。

### 消费只触发订单事实复核

OrderPaymentTimeoutConsumer 解析命令后调用生命周期 Service。后者持有订单行锁：非待支付直接结束；仍待支付但未到期返回未完成让消息重试；确实到期才执行 CLOSED、返可售/消除锁定量、释放券、记录库存 RETURN 和 CANCELLED 事件。支付与手动取消竞争同一订单行锁。

不依赖 Redis DONE 表示关单结果：数据库状态直接保护重复；提前消息不能先标“已完成”导致永远不再检查。消息时间仅用于投递，不能覆盖数据库真实期限。

### 扫描继续作为独立兜底

保留现有到期订单扫描及同一事务入口，处理 MQ 关闭、发送积压、消费失败和死信；Redis 返还失败依靠已保存任务和 Lua 幂等恢复。未承诺定时消息零延迟。

## Data and Compatibility

- MySQL 与 H2 均新增 V16__order_payment_timeout.sql，为 Outbox 增加可空正数 deliver_at_epoch_ms；旧事件为空并按即时消息发送。
- OrderMessageDO 保留普通消息构造方式，新增带时间的构造方式；所有新查询读取时间字段。
- 默认配置/构造回退为 900 秒，环境覆盖 ORDER_PAYMENT_TTL_SECONDS；新消费组使用 ROCKETMQ_TIMEOUT_CONSUMER_GROUP。
- 新订单使用新默认；既有 payment_expires_at 不回写。异步订单只在真正建单时开始窗口。

## Failure and Recovery

| 位置 | 处理 |
|---|---|
| 建单事务失败 | 订单与定时意图共同回滚 |
| 首次发送失败 | 重发原 ID / 原文 / 绝对截止时间 |
| 已过期才补发 | 立即触发检查 |
| 消息提前或内容时间伪造 | 查询数据库，不提前关闭，允许重试 |
| 重复到达或已支付 | 原订单状态阻止重复副作用 |
| 关单提交、Redis 返还失败 | 持久化 RETURN 任务幂等补做 |
| 消费链路失败 | 到期扫描兜底 |

## Validation

用 H2 验证交易、重复和竞争，真实 Redis 验证关闭后的可售/pending/状态守恒，独立 MySQL 验证 V16 兼容，真实 5.3.2 Broker 验证同步/异步新订单定时关闭。证据与限制见 verification。

## Rollout / Rollback

升级前需有数据库迁移权限与支持定时投递的 Broker，应用发送器和消费入口随版本更新。回退应用不能删除已持久化期限、超时意图或库存返还任务；MQ 关闭期间保留数据库到期扫描。已应用 V16 不通过篡改历史迁移回退。
