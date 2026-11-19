# MQ、Outbox 与故障恢复

返回 [文档导航](README.md) · 正常交易步骤见 [下单设计](order-design.md)

[![Outbox 可靠异步和持久化故障恢复](images/outbox-recovery.png)](images/outbox-recovery.svg)

上半部表示可靠受理到真正建单，下半部说明不同中断位置需要哪些恢复凭据。 点击图片查看可缩放 SVG。


## 1. MQ 承接建单、延时关单与审计

| Tag | 与什么一起落库 | 消费做什么 |
|---|---|---|
| ORDER_BUILD | PROCESSING 受理状态与处理期限 | 从后端 trace 快照真正建立订单 |
| ORDER_PAYMENT_TIMEOUT | 建单事务，保存原支付截止 epoch 毫秒 | 定时检查未支付订单并幂等关单 |
| ORDER_CREATED | 建单事务 | 保存审计事件回执 |
| ORDER_PAID | 支付事务 | 保存审计事件回执；不产生真实扣款 |
| ORDER_CANCELLED | 取消/超时关单事务 | 保存审计事件回执 |

建单、超时关单、事件审计使用各自的消费组。当前后面三类事件没有接入真实仓储、配送或退款，不能把审计回执说成履约已经完成。

## 2. 为什么多一张 Outbox 表

普通数据库事务只能原子提交本库操作，不能顺便保证远程 MQ 也提交。

```text
异步受理数据库事务
  trace → PROCESSING / RESERVED
  INSERT ORDER_BUILD Outbox
  COMMIT
       ↓
提交后唤醒转发器；后台扫描补漏
  条件抢占发送租约 → 发 RocketMQ → 按所有者令牌标记 SENT
       ↓
消费者幂等进入建单事务 → COMMIT → trace SUCCESS + orderId
```

若写受理结果后进程崩溃，Outbox 仍保存“这条消息需要发送”。若 Broker 收到消息后本地未成功标记 SENT，下次会重发同一 eventId。因此采用至少一次投递和幂等消费。

ORDER_BUILD 的 eventId 由 trace 生成稳定编号，正文只传 traceId；消费者读取服务端绑定的快照。生命周期事件的 eventId 在持久化时生成，发送重试复用原 ID。

超时关单也通过 Outbox 保存，`deliver_at_epoch_ms` 是固定的 Broker 投递时刻，`next_attempt_at` 是发送失败后的重试时刻，两者职责不同。同步和异步订单都在实际建单后写此命令；不是在异步受理时开始支付倒计时。详见 [下单设计的支付与超时部分](order-design.md#8-支付取消与超时防超卖也要防库存泄漏)。

## 3. 三种记录分别回答什么

| 记录 | 可以证明 | 不能证明 |
|---|---|---|
| order_message_outbox.SENT | 消息已经发送给 MQ | 消费者已成功建单 |
| order_event_receipt | 对应生命周期事件已保存审计回执 | 库存或订单又执行了一次；真实履约完成 |
| order_submit_trace.SUCCESS + orderId | 建单数据库事务已提交 | 已付款；后置整理和消息全部追平 |

不是每条 MQ 投递都插入一条新的订单；重复投递应该被幂等识别。同一事件的审计回执以 eventId 唯一。

## 4. 消费失败怎么分类

- 临时连接、数据库等基础设施异常：抛异常，让 MQ 重试；Redis 消费标记不能抢先写 DONE。
- 库存、报价、券等明确业务拒绝：建单事务回滚后，在独立事务记录失败及释放进度；该消息可以正常结束消费。
- 已经 SUCCESS 或已进入其他终态：重复消费返回原结果，不再扣库存或创建订单。

正常 ACK 只说明消费处理完成；业务拒绝也可能正常 ACK。成功建单 TPS 在数据库事务提交后计数。

## 5. 故障位置决定恢复动作

| 中断位置 | 恢复凭据 | 恢复动作 |
|---|---|---|
| Lua 前或多 SKU 预扣一半 | 已提交 RESERVING 与购买快照 | 宽限期后逐 SKU 幂等释放 |
| 预扣成功，受理事务失败 | trace 意图与 Redis 标志 | 原 trace 期限内重试；过期释放 |
| 受理提交，MQ 尚未发出 | Outbox | 扫描重发 |
| 建单事务失败 | 原 trace / MQ 命令 | 临时失败重试，明确拒绝记录 FAILED |
| 建单成功，Redis 确认失败 | SUCCESS / CONFIRM_PENDING | 只补 CONFIRM，不能重新建单或释放成功购买 |
| 关单提交，Redis 返还失败 | order_inventory_return | 重试 RETURN |
| Lua 已完成，数据库整理进度未记上 | Redis 终态 Hash | 重试只补进度，不再次改变计数 |
| 交易库存池丢失 | 数据库库存、在途 trace 和历史预占 | 停止相关接单并核对；尚无自动重建 |

Redis 与 MySQL 不在一个原子事务中。恢复线索要先独立提交，失败记录要在建单回滚之后保存，不能把所有内容塞进一个最后会回滚的事务。

## 6. 为什么可靠不等于吞吐足够

当前即时转发、库存整理都是有界线程池；满时可能丢失唤醒，持久化任务仍在，由定时扫描恢复。

默认 Outbox 每 5 秒扫描最多 20 条，仅约 4 条/秒，尚未扣发送耗时；库存恢复每 5 秒最多 50 条，约 10 条/秒。它们适合少量补漏，长时间依赖扫描就会积压。

压测已经观察到订单成功但审计 Outbox 积压，以及大量 CONFIRM_PENDING。优化方向是分别测即时发送/整理吞吐、批量扫描与重试速度、监控最老任务；同时保留租约和幂等。不能只调大发压速率就宣称链路支持更高持续 TPS。

配置入口：[application.yml](../src/main/resources/application.yml)。关键源码：[OrderBuildCommandService](../src/main/java/com/bitmall/agent/order/service/OrderBuildCommandService.java)、[InventorySettlementService](../src/main/java/com/bitmall/agent/order/inventory/InventorySettlementService.java)、[OrderRecoveryService](../src/main/java/com/bitmall/agent/order/service/OrderRecoveryService.java)。

## 7. 异步建单死信怎么处理

建单监听器配置最多重试 5 次。无法正常处理的消息由 RocketMQ 隔离到 `%DLQ%<建单消费组>`，例如 `%DLQ%wowo_mall_order_build_dev`。建单成功后的重复投递和已持久化的明确业务失败都可以正常确认。数据库连接异常或找不到对应的购买记录会抛异常，经过 Broker 重试。死信计数上涨代表消息处理异常，购买结果仍由数据库确定。

| 场景 | 现有业务处理 |
|---|---|
| 发给 Broker 失败 | Outbox 回到 PENDING，记录下次发送时间。 |
| 消费者明确拒绝购买 | 回滚建单事务，另开事务记录失败与待释放，随后通过原预占记录幂等释放 Redis。 |
| 临时消费失败 | Broker 重试。购买未到期且符合恢复条件时，数据库恢复任务也可补投原 Outbox，沿用 eventId。 |
| 建单窗口已到且仍未建单 | 恢复任务锁住购买记录再提交 QUEUE_TIMEOUT，随后释放库存。待发或发送中的建单 Outbox 转为 CANCELLED，之前已确认为 SENT 的记录保留发送结果。 |
| 其他投递已成功 | 保留成功结果，只跟踪尚未完成的库存确认。 |

开启 `ROCKETMQ_BUILD_DLQ_ENABLED=true` 后，应用用独立组 `<建单消费组>_dlq` 订阅建单组的死信 Topic，并读取此前仍可用的死信。监听器用消息 Key `eventId` 和正文里的 `traceId` 核对原 Outbox，再调用表中的原有恢复逻辑。

| 死信消费者查到什么 | 怎么办 |
|---|---|
| 异步购买仍有效 | 按原规则检查已发送间隔，符合条件时重新排入原 Outbox。购买截止时间沿用原值。 |
| 购买超时且尚未建单 | 行锁下写入超时结果，按预占记录幂等释放库存。 |
| 购买已经是成功或失败 | 只处理尚待确认或待释放的预占。 |
| 消息格式错误，或对不上原购买及 Outbox | 记录 Broker 消息编号及可用的业务编号，结束这条死信的消费。 |
| MySQL 查询或提交异常 | 交给 RocketMQ 重试死信处理。若处理仍耗尽重试，消息进入该独立组的死信队列，运维可在 Dashboard 查看。Redis 整理失败时，待整理状态由现有扫描继续重试。 |

Prometheus 负责告警，独立消费者负责逐条核对，定时扫描在两种订阅开关下都继续运行。运维在 Dashboard 和应用日志核查异常。若报“无法匹配原建单记录”，核对生产和消费实例使用的 Topic、消费组和数据库是否属于同一环境。 [告警配置](pressure-monitoring.md#6-异步建单与死信告警) · [启用说明](deployment.md#异步建单死信消费者)
