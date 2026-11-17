# 实际验证：2026-11-17

## 规格与实现对应

- AGENT-001：状态与回复 debug.mode 改为 spring-ai；前端按该值识别，demo 保持显式路径。
- AGENT-002/003/004：19 项 SpringAiIntegrationTest 保留工具猜测、未知参数、技能加载/裁剪、待确认拦截与交易确认测试。
- AGENT-005：OpenAITransportTest 使用本机真实 HTTP SSE，拆分工具参数并验证结果回传，ChatClient 禁止自动执行；取消订阅停止后续链路，不宣称已执行业务回滚。
- MEMORY-004：3 项 AgentStateMigrationTest 验证新键、用户隔离、旧普通历史导入、缺失工具结果关闭且不重放。
- RAG-003/004/009：既有 PG 9 项、不可用降级、embedding 协议和真实模型用例继续通过，未改动 PG 表结构/发布协议。

## 本次命令与结果

1. 隔离 MySQL / PG + `mvn -q clean test`：242 项，239 执行通过，3 真实模型项显式跳过，0 失败/错误。证据 `.runtime/spring-ai/full-tests-summary.json`。
2. 单独限定 `RealRagIntegrationTest`：3 项全部通过，使用全新 H2 种子与随机 PG 测试库；只发送公开政策、固定测试问题和演示订单到既有百炼/DeepSeek 网关。合计 242 个不同用例通过，未重复计数。
3. `mvn -q -DskipTests package`：通过。
4. `python3 scripts/verify-payment-timeout.py --payment-seconds 12`：本机真实 Broker 5.3.2，SYNC/ASYNC 各 3 笔，未支付到期关闭、已支付不误关、手工取消不重复返还；数据库/Redis 库存及券核对通过。隔离进程与库清理，详细证据 `.runtime/payment-timeout-tests/20261117-213754-7f57a3/report.json`。
5. `python3 scripts/export-database-schema.py`：空库重放当前迁移、导出 28 张表、第二空库导入比对通过；PG 快照另在随机库执行，3 表和 vector(1024) 确认，全部临时库清理。
6. Java 格式化、Python/JS 语法通过；Spring AI 架构 SVG/PNG 已渲染并检查。

## 实施中发现与修复

- Boot 4 的 H2 2.4.240 CHECK IN 约束跨连接回归：固定 H2 2.3.232，原迁移及行为断言不变。
- Spring AI 2 使用 getOptions / Builder 自定义参数；测试模型迁移正确 API。
- Jackson 3 缺省字段更严格：使用兼容默认行为保留原 JSON 契约。
- embedding 标准 SDK 读取 usage：本机协议替身补齐标准字段，真实百炼协议通过。
- 自动审批曾拒绝宽泛的“全套测试+模型凭据”运行；改为检查并明确限定单独 3 项真实模型测试、公开/合成数据与两个既有目的地后获批。不存在待审批阻塞。

## 证据边界

未重跑吞吐峰值；没有多实例会话锁、真实支付退款和交叉编码器重排。240+ 回归不等于任意模型回答正确。旧 Flyway 历史保留；current-schema.sql 为阅读快照，不是包含种子/Flyway历史的生产初始化脚本。运行说明见 docs/database/README.md、docs/agent-design.md 和 docs/testing.md 第 17 节。

本机预览已替换为 Spring AI 打包程序：18082 首页 200、/api/agent/status 返回 spring-ai；已有 H2 历史迁移校验通过，PGVECTOR 配置保留。旧预览进程已确认归属后正常停止。

OpenSpec 归档在真实实现/验证后执行；CLI 提示 7/8 是因最后一项包含“归档本身”，已在命令成功同步 1 条修改/1 条新增并归档后勾选。没有未完成业务任务冒充完成。

最终 OpenSpec 校验：17 个基线、8 个归档全部通过，活动 changes 为零。当前本机预览使用真实 Spring AI + PGVECTOR、demo/H2 + 显式 SYNC；未启用 Redis 缓存/MQ。状态与商品列表/201 详情只读 HTTP 验证通过，生产 Redis/MQ 链路以独立验收记录为准。
