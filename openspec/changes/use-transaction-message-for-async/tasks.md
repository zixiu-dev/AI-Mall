# 实施任务

## 1. 配置与基础设施

### 1.1 增加 TransactionMQProducer 配置
- [ ] 创建 `RocketMQTransactionConfig` 配置类
- [ ] 配置事务监听器线程池
- [ ] 配置 producer 参数（namesrv、group 等）
- **验证方式：** 启动应用，检查 producer 是否正常注册

### 1.2 实现 TransactionListener
- [ ] 创建 `OrderTransactionListener` 实现类
- [ ] 实现 `executeLocalTransaction` 方法（更新 trace）
- [ ] 实现 `checkLocalTransaction` 方法（回查 trace 状态）
- **验证方式：** 单元测试验证三种返回值（COMMIT/ROLLBACK/UNKNOWN）

## 2. 核心改造

### 2.1 改造异步受理发送逻辑
- [ ] 修改 `OrderSubmissionService.admitAsync` 使用事务消息
- [ ] 增加配置开关 `order.use-transaction-message`
- [ ] 保留原 Outbox 逻辑（灰度切换）
- **验证方式：** 集成测试验证开关切换

### 2.2 本地事务简化
- [ ] `executeLocalTransaction` 只更新 trace 表
- [ ] 删除 Outbox 插入逻辑
- [ ] 确保异常时正确返回 ROLLBACK_MESSAGE
- **验证方式：** 模拟异常验证事务回滚

### 2.3 回查接口实现
- [ ] 根据 traceId 查询 trace 表
- [ ] 判断 status 返回正确状态
- [ ] 处理 trace 不存在情况
- **验证方式：** 模拟网络中断验证回查机制

## 3. 测试与验证

### 3.1 单元测试
- [ ] `OrderTransactionListener` 测试
- [ ] 本地事务成功/失败场景
- [ ] 回查逻辑测试
- **关联要求：** MSG-001, SUBM-004

### 3.2 集成测试
- [ ] 异步建单端到端测试
- [ ] 本地事务失败不发送消息
- [ ] 消费者正常消费
- [ ] 幂等控制仍然生效
- **关联要求：** MSG-001, SUBM-004

### 3.3 性能测试
- [ ] 消息延迟对比（Outbox vs 事务消息）
- [ ] 数据库写入次数对比
- [ ] 并发场景验证
- **验证方式：** 压测记录延迟 P99

## 4. 清理与文档

### 4.1 删除旧代码（完全切换后）
- [ ] 删除 Outbox 扫描任务（ORDER_BUILD 相关）
- [ ] 保留 Outbox 表（订单事件仍使用）
- [ ] 清理配置项

### 4.2 更新文档
- [ ] 更新 `docs/order-design.md`（异步流程）
- [ ] 更新 `docs/messaging-recovery.md`
- [ ] 补充事务消息回查机制说明

## 5. 灰度与上线

### 5.1 灰度验证
- [ ] 10% 流量使用事务消息（1 天）
- [ ] 50% 流量（1 天）
- [ ] 100% 流量（观察 1 周）
- **验证方式：** 监控消息延迟、错误率、回查频率

### 5.2 回滚预案
- [ ] 配置开关可立即切回 Outbox
- [ ] 两套逻辑并存期间数据一致
- **验证方式：** 演练回滚流程
