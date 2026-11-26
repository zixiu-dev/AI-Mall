# 项目约定

[当前功能与实现](README.md)按交易流程介绍业务。[维护流程](workflow.md)说明如何更新现有规格。

## 代码与业务边界

- Java 17 普通类，允许按需使用 Lombok。普通依赖注入使用 `@RequiredArgsConstructor` 与 `final` 字段，每个依赖字段写清中文职责；需要构造时完成资源初始化或校验的类保留定制构造器。类、关键方法与复杂步骤写清中文职责。按业务模块组织 Controller、Service 和 DAO，页面与 Agent 复用业务服务。
- MySQL 保存订单、券、购买状态与可恢复进度。展示缓存只负责查询加速。Redis 访问使用项目已有的 Redisson 封装、原生对象和 Lua。交易 Redis 与商品缓存隔离。
- 同一 trace 最多成功建单一次。Redis 幂等保护之后，用 MySQL 行锁、状态条件、库存条件更新及唯一约束保证结果。同步和异步采用相同的成交规则。
- 可靠受理返回 `PROCESSING`，建单事务提交后才返回 `SUCCESS` 和订单号。MQ 发送和消费状态单独记录。支付与取消共用订单状态校验，重试沿用已保存的截止时间。
- Agent 写操作以用户批准的后端参数执行业务。确认卡结果、购买结果和实际支付状态各自从对应持久化记录读取。

## 验证与迁移

- MySQL、H2 和 PostgreSQL 的空库分别通过 Flyway 基线和种子脚本初始化。已有库按需从 `DB_HISTORY_DIR` 读取原样保留的迁移。PostgreSQL 向量索引用独立 schema 和迁移入口。
- Redis Lua、SQL 方言以及 RocketMQ 投递使用相应的隔离组件验证。MQ 测试替身和真实 Broker 的结果分开报告。
- 记录实测环境、统计窗口、请求成功、订单提交以及积压。密钥和私有连接信息保存在本机配置中。
- 按 [验证映射](verification-matrix.md)查测试。启动和测试结果分别见 [运行文档](../docs/deployment.md) 与 [测试总文档](../docs/testing.md)。
