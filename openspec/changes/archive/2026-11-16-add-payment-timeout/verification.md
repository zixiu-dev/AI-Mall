# 定时关单：实际验证记录

## Scope

功能实现和真实测试日期：2026-11-16。本文沿用此前实际执行结果；本次补写 OpenSpec 没有重新发压或将补写文档算作新的业务回归。

## Results

| 要求 / 场景 | 实际检查 | 结果 | 证据 |
|---|---|---|---|
| LIFE-001 默认 900 秒、稳定截止 | 新订单真实事务读回期限与定时字段 | 通过 | [OrderPaymentTimeoutIntegrationTest](../../../../src/test/java/com/bitmall/agent/order/service/OrderPaymentTimeoutIntegrationTest.java) |
| LIFE-002 绝对时刻重发、原事务回滚 | 发送适配替身与实际数据库回滚 | 通过；不是实际 Broker 发送测试 | [消息事务测试](../../../../src/test/java/com/bitmall/agent/order/service/OrderMessagingIntegrationTest.java) |
| LIFE-003 提前、重复、已付、已取消、并发 | 实际生命周期事务与消费入口 | 通过 | [超时测试](../../../../src/test/java/com/bitmall/agent/order/service/OrderPaymentTimeoutIntegrationTest.java) |
| 库存关闭重复补偿 | 真实 Redis + Lua + 数据库关单 | 通过 | [InventoryReservationIntegrationTest](../../../../src/test/java/com/bitmall/agent/order/service/InventoryReservationIntegrationTest.java) |
| V16 与全项目回归 | 独立 MySQL、真实 Redis 和完整 Maven suite | 170 项、0 失败、0 错误、0 跳过 | [统一测试说明](../../../../docs/testing.md) |
| 真实定时投递 | 本机 MySQL / 两个 Redis / MQ 5.3.2，SYNC 和 ASYNC 各 3 单 | 六条定时消息都消费；未付 CLOSED、已付 PAID、手动取消 CANCELLED | [本机 report.json](../../../../.runtime/payment-timeout-tests/20261116-212709-b0e536/report.json) |

## Boundaries

真实投递测试只把本轮有效期缩为 12 秒；默认 900 秒由配置和事务用例核对，未让测试真的等待十五分钟。扫描间隔设为一小时，证明本轮新订单由 MQ 消费入口关闭；正式配置继续默认扫描兜底。

各专用 SKU 初始 10 件，最终 stock=9、locked_stock=0、Redis available=9、pending=0；用于未支付购买的券恢复 AVAILABLE。两组到期后观察关闭约 9.1 秒和 1.0 秒，故不声称定时检查严格零延迟。

临时进程和随机库已经清理。本机 .runtime 证据不随 Git 克隆；按 [验证脚本](../../../../scripts/verify-payment-timeout.py) 可重新执行。未验证共享开发 Broker 的部署能力，也未重新测量增加定时消息之后的峰值 TPS。

## Completion

代码、相关业务验证、说明与图已完成。当前规格已包含该功能，此目录仅保存追溯历史。OpenSpec 文件结构和归档任务完整性是本次另外检查，不替代上表业务检查。
