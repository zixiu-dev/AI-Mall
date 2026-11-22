# 依赖注入重构验证

验证日期为 2026-11-22。Java 17，Lombok 版本由 Spring Boot BOM 管理。

## 代码范围

- 102 个普通注入组件使用 @RequiredArgsConstructor，构造参数顺序沿用原入口。
- 334 个依赖或相关字段补充职责说明，102 个 Lombok 类的注入字段注释检查通过。
- 具名 Redis/执行器和配置值注解由 lombok.config 复制到生成参数。
- 17 个存在资源初始化、监控注册或构造期校验的类保留原定制构造器，依赖字段仍补注释。CacheMetrics 保留单参数测试便捷入口，Spring 使用 Lombok 生成并标注 Autowired 的完整构造器。

## 实际执行

- `mvn -q -DskipTests compile` 和 `mvn -q -DskipTests test-compile` 通过。
- `mvn -q '-Dtest=!RealRagIntegrationTest,!PgVectorIndexIntegrationTest,!MySQLMigrationIntegrationTest,!KnowledgeMySQLIndexIntegrationTest' test` 通过，33 个测试类、241 个测试，失败 0、错误 0。
- 覆盖 Spring 装配、临时独立 Redis、多级缓存和布隆过滤器、同步/异步下单与库存恢复、MQ 消费处理、Agent SSE/确认/会话、RAG 索引与检索等既有用例。
- 字节码检查确认 AfterSalesTools 的依赖构造器由 Lombok 生成；服务启动与缓存/交易集成测试校验依赖装配。
- OpenSpec 配置 skip_specs 为 true，严格校验 20 项通过，没有新增业务差异。
- `git diff --check` 通过。

## 验证边界

MySQL、pgvector 外部服务与真实模型用例明确排除。消息集成测试使用原有传输替身与真实消费者逻辑，本轮没有连接真实 Broker。Redis 用本机隔离实例。源码重构保留既有功能契约，未将这些结果描述为生产性能验证。
