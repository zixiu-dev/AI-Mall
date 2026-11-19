# 实施任务

## 1. 数据库设计

### 1.1 创建对账日志表
- [ ] 创建 Flyway 迁移脚本 `V20__inventory_reconciliation_log.sql`
- [ ] 定义表结构（sku_id, redis/db 值, 差异, 动作, 时间）
- [ ] 添加索引（sku_id + check_time）
- **验证方式：** 在 H2 和 MySQL 上执行迁移

### 1.2 增加 Mapper 接口
- [ ] `InventoryReconciliationMapper` 接口
- [ ] 插入对账日志方法
- [ ] 查询对账历史方法
- **验证方式：** 单元测试

## 2. 核心对账逻辑

### 2.1 实现对账服务
- [ ] 创建 `InventoryReconciliationService`
- [ ] 实现库存数量对账 `reconcileStock()`
- [ ] 实现预占记录对账 `reconcileReservations()`
- [ ] 实现 trace 状态对账 `reconcileTraceStatus()`
- **关联要求：** STOCK-005

### 2.2 库存数量对账
- [ ] 查询 MySQL stock
- [ ] 查询 Redis available
- [ ] 计算差异并分类处理（≤5 自动修正，>5 告警）
- [ ] 记录对账日志
- **验证方式：** 单元测试模拟差异场景

### 2.3 预占记录对账
- [ ] 扫描 Redis 预占记录
- [ ] 对比 trace 表检查孤儿预占
- [ ] 清理不存在 trace 的预占
- [ ] 清理过期终态预占（7 天）
- **验证方式：** 集成测试验证清理逻辑

### 2.4 trace 状态对账
- [ ] 查询长时间 CONFIRM_PENDING 的 trace
- [ ] 补执行 CONFIRM 操作
- [ ] 查询长时间 RETURN_PENDING 的返还
- [ ] 补执行 RETURN 操作
- **验证方式：** 模拟 PENDING 状态验证补偿

### 2.5 告警机制
- [ ] 集成告警服务（日志/钉钉/邮件）
- [ ] Redis 库存丢失告警
- [ ] 大差异告警（> 5 件）
- **验证方式：** 模拟差异触发告警

## 3. 定时任务配置

### 3.1 配置 Scheduled 任务
- [ ] `@Scheduled(cron = "0 0 * * * ?")` 每小时执行
- [ ] 配置任务线程池
- [ ] 增加配置开关 `inventory.reconciliation.enabled`
- **验证方式：** 启动应用检查任务调度

### 3.2 任务执行监控
- [ ] 记录任务开始/结束时间
- [ ] 记录处理 SKU 数量
- [ ] 记录发现问题数量
- **关联要求：** OBS-004

## 4. 监控与可观测性

### 4.1 增加对账指标
- [ ] `inventory_reconciliation_total`（Counter）
- [ ] `inventory_reconciliation_diff_count`（Counter）
- [ ] `inventory_reconciliation_auto_fix`（Counter）
- [ ] `inventory_reconciliation_alert`（Counter）
- [ ] `inventory_reconciliation_orphan_cleared`（Counter）
- [ ] `inventory_reconciliation_duration`（Histogram）
- **关联要求：** OBS-004

### 4.2 暴露监控端点
- [ ] `/actuator/metrics/inventory.reconciliation.*`
- [ ] `/admin/inventory/reconciliation/logs` 查询接口
- **验证方式：** 调用接口验证指标

## 5. 测试与验证

### 5.1 单元测试
- [ ] `InventoryReconciliationService` 各方法测试
- [ ] Mock Redis 和 MySQL 模拟差异
- [ ] 验证自动修正逻辑
- [ ] 验证告警触发条件
- **关联要求：** STOCK-005

### 5.2 集成测试
- [ ] 真实 Redis 环境测试
- [ ] 制造差异验证对账
- [ ] 验证孤儿预占清理
- [ ] 验证终态预占清理
- **关联要求：** STOCK-005

### 5.3 性能测试
- [ ] 1000 个 SKU 对账耗时
- [ ] 10000 条预占记录扫描耗时
- [ ] 对账对业务的影响（CPU/内存）
- **验证方式：** 压测记录耗时

## 6. 文档更新

### 6.1 更新设计文档
- [ ] `docs/order-design.md` 补充对账章节
- [ ] `docs/data-design.md` 增加对账日志表
- [ ] 新增 `docs/reconciliation-design.md`（可选）

### 6.2 更新运维文档
- [ ] `docs/deployment.md` 增加对账配置
- [ ] 告警规则配置说明
- [ ] 对账日志查询方法

### 6.3 更新测试文档
- [ ] `docs/testing.md` 补充对账测试
- [ ] 记录对账相关测试用例

## 7. 上线与观察

### 7.1 灰度上线
- [ ] 先在测试环境运行 1 周
- [ ] 观察对账发现的问题
- [ ] 调整差异阈值（默认 5）
- **验证方式：** 查看对账日志

### 7.2 生产观察
- [ ] 监控对账任务执行情况
- [ ] 统计差异频率和类型
- [ ] 优化对账策略
- **验证方式：** 7 天观察期，记录问题
