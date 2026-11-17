# Redisson 统一与缓存顺序：验证记录

## Scope

日期 2026-11-16；关联 CACHE-002 / CACHE-003。MQ 消费去重维持原标记适配，其余 Redis 业务访问采用 Redisson。未修改表结构和交易库存键。

## Results

- 参数校验、锁前 Bloom 拦截、本地命中不访问 Bloom：真实缓存入口检查通过。
- 原生 RBloomFilter 跨独立客户端、丢位图后保留未提交登记、回滚误判兜底：真实 Redis 检查通过。
- 两个独立 Redisson 客户端的缓存合并、看门狗跨 600ms 测试窗口续期、模拟失锁后旧所有者不能回填/解锁：通过。
- 全量真实组件回归：174 项、0 失败、0 错误、0 跳过，含独立 MySQL 和真实 Redis。
- 两个独立 JVM：64 冷 key × 8 = 512 个 HTTP 200，逐条核对商品/SKU；数据库回源与回填各 64。202 次拿锁中 138 次锁后重查命中，559 次等锁检查循环。
- 真实 MQ 5.3.2：SYNC / ASYNC 各三笔订单，未付关闭、已付保持、手动取消保持；Redis / 数据库库存和券一致。

## Evidence

- [统一测试说明](../../../../docs/testing.md#11-redisson-统一与缓存顺序验证)
- [全量回归汇总](../../../../.runtime/redisson-cache-tests/20261116-221729-313797/regression-summary.json)
- [双 JVM 证据](../../../../.runtime/redisson-cache-tests/20261116-221729-313797/report.json)
- [消息复验](../../../../.runtime/payment-timeout-tests/20261116-221638-fd1cb6/report.json)
- [缓存验证脚本](../../../../scripts/verify-redisson-cache.py)

## Boundaries

本轮是正确性验证，不是新峰值 QPS/TPS；两个应用客户端共享单机 Redis，不等于 Redis Cluster/故障切主验证。定时消息测试仍用 12 秒窗口，正式默认 900 秒；原始 .runtime 文件不随 Git 克隆。

新缓存锁和 Bloom 私有键版本变化，切换时停止旧构建实例。库存原键、编码与状态不变，不能清空交易 Redis。原生 Bloom 采用单调增量补全，回滚登记只增加误判；中断遗留的待登记项需后续运维清理，不宣称已有自动垃圾回收。

## Completion

实现、关联及全量回归、跨 JVM 和真实 MQ 验证、必要说明与配图已完成，可同步当前规格并归档。正式部署没有在本轮执行。
