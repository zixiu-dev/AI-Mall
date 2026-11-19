## 2026-11-19 验证记录

| 范围 | 实际验证 |
|---|---|
| OBS-004 采样失败 | `OrderBuildBacklogMonitorTest` 验证首次采样前为未知、成功采样后有积压值、模拟查询失败后保留上次值和时间。 |
| MQ-005 与 OBS-004 告警规则 | Prometheus 3.5.0 的 `promtool check config` 和 `promtool test rules` 通过。覆盖首条死信、历史计数不重复告警、后续增量、其他组隔离、积压和采样失效、Broker 抓取故障。 |
| MQ-005 真实 Broker 与 Prometheus | `scripts/verify-order-build-dead-letter.py --prometheus-bin <可执行文件>` 通过。向隔离 RocketMQ 5.3.2 发送引用不存在购买记录的建单消息，应用日志记录首次失败和 5 次重试，按原消息 Key 在建单组死信 Topic 查到消息，Broker 原生指标为 1，Prometheus 的 `AsyncOrderBuildDeadLetter` 进入 `firing`。随后同组的有效购买得到 `SUCCESS / CONFIRMED`，可售从 10 降到 8。 |
| OBS-004 真实 MySQL 三类积压 | 注入独立测试行后，管理端报告待建单 1、待确认 1、待释放 2，最老年龄分别为 72、82、102 秒，三个旧同步行均被排除。Prometheus 暴露了采样时间和三类指标。 |
| 交易回归 | `mvn -o -B package` 成功，250 项中 229 项通过、21 项按配置跳过，零失败或错误。现有库存集成测试继续验证同一购买过期后只释放一次，迟到的建单消息不能成功。 |
| 原关单链路 | 使用更新后的隔离环境运行 `scripts/verify-payment-timeout.py`。同步和异步各 3 笔订单通过，真实消费 6 条定时检查，两组 Redis 与数据库可售均为 9。 |

自动化运行使用临时数据库、Redis、Broker 和本机进程。Prometheus 规则验证覆盖告警计算，不包含短信、邮件或其他通知渠道的接收。未运行峰值压测。

隔离 Broker 只缩短了测试用的消费重试间隔，消费次数沿用线上配置。验证过程中解决了隔离 Broker 和 Topic 同名、Topic 未注册就发送，以及原生 Prometheus 指标行附带时间戳的问题。最终报告保存在本机忽略提交的 `.runtime/order-build-dlq/20261119-211430-b61b33/report.json`，关单回归报告位于 `.runtime/payment-timeout-tests/20261119-211242-3f6cae/report.json`。
