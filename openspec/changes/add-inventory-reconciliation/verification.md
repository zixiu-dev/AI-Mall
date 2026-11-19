# 验证记录

## 验证环境

- Spring Boot: 4.1.2
- Redis: 7.0
- MySQL: 8.0

---

## 需求验证

### STOCK-005: 库存对账

#### Scenario: 库存数量对账

**测试方法：**
```java
// 1. 制造差异
skuMapper.updateStock(skuId, 100);  // MySQL = 100
redis.set("inventory:v1:{" + skuId + "}:available", 97);  // Redis = 97

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证
Integer redisStock = redis.get("inventory:v1:{" + skuId + "}:available");
assertEquals(100, redisStock);  // Redis 已修正

// 4. 检查日志
ReconciliationLog log = logMapper.selectLatest(skuId);
assertEquals("AUTO_FIX", log.getAction());
assertEquals(3, log.getDifference());
```

**实际结果：** 待验证

**证据：** 待提供测试日志

---

#### Scenario: 大差异告警

**测试方法：**
```java
// 1. 制造大差异
skuMapper.updateStock(skuId, 100);  // MySQL = 100
redis.set("inventory:v1:{" + skuId + "}:available", 90);  // Redis = 90，差异 10

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证
Integer redisStock = redis.get("inventory:v1:{" + skuId + "}:available");
assertEquals(90, redisStock);  // Redis 未修正

// 4. 检查告警
ReconciliationLog log = logMapper.selectLatest(skuId);
assertEquals("ALERT", log.getAction());
assertTrue(alertService.hasAlert("库存差异过大"));
```

**实际结果：** 待验证

**证据：** 待提供

---

#### Scenario: 孤儿预占清理

**测试方法：**
```java
// 1. 制造孤儿预占
redis.hset("inventory:v1:{" + skuId + "}:reservation:ORPHAN", 
    "quantity", 1, "state", "RESERVED");
// trace 表中不存在 ORPHAN

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证
Boolean exists = redis.exists("inventory:v1:{" + skuId + "}:reservation:ORPHAN");
assertFalse(exists);  // 已清理
```

**实际结果：** 待验证

**证据：** 待提供

---

#### Scenario: 终态预占清理

**测试方法：**
```java
// 1. 制造过期终态预占
String traceId = "OLD_TRACE";
traceMapper.insert(new Trace(traceId, "SUCCESS", 
    LocalDateTime.now().minusDays(8)));  // 8 天前
redis.hset("inventory:v1:{" + skuId + "}:reservation:" + traceId,
    "quantity", 1, "state", "CONFIRMED");

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证
Boolean exists = redis.exists("inventory:v1:{" + skuId + "}:reservation:" + traceId);
assertFalse(exists);  // 已清理
```

**实际结果：** 待验证

**证据：** 待提供

---

#### Scenario: trace 状态补偿

**测试方法：**
```java
// 1. 制造 CONFIRM_PENDING
traceMapper.insert(new Trace(traceId, "SUCCESS", "CONFIRM_PENDING",
    LocalDateTime.now().minusMinutes(10)));  // 10 分钟前

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证
Trace trace = traceMapper.selectById(traceId);
assertEquals("CONFIRMED", trace.getInventoryStatus());  // 已补确认
```

**实际结果：** 待验证

**证据：** 待提供

---

#### Scenario: Redis 库存丢失检测

**测试方法：**
```java
// 1. 模拟库存丢失
skuMapper.updateInitialized(skuId, true);  // 标记已初始化
redis.del("inventory:v1:{" + skuId + "}:available");  // 删除 Redis

// 2. 执行对账
reconciliationService.reconcile();

// 3. 验证告警
assertTrue(alertService.hasAlert("Redis 库存丢失"));
ReconciliationLog log = logMapper.selectLatest(skuId);
assertEquals("ALERT", log.getAction());
assertNull(log.getRedisAvailable());
```

**实际结果：** 待验证

**证据：** 待提供

---

### OBS-004: 对账监控指标

#### Scenario: 对账指标采集

**测试方法：**
```java
// 1. 执行对账
reconciliationService.reconcile();

// 2. 验证指标
MeterRegistry registry = meterRegistryProvider.get();

Counter total = registry.counter("inventory.reconciliation.total");
assertTrue(total.count() > 0);

Counter diffCount = registry.counter("inventory.reconciliation.diff_count");
// 根据测试数据验证
```

**实际结果：** 待验证

**证据：** 待提供 Prometheus 截图

---

#### Scenario: 对账日志记录

**测试方法：**
```java
// 1. 制造差异并对账
// ...

// 2. 查询日志
List<ReconciliationLog> logs = logMapper.selectBySku(skuId);
assertFalse(logs.isEmpty());

ReconciliationLog log = logs.get(0);
assertEquals(skuId, log.getSkuId());
assertNotNull(log.getCheckTime());
assertNotNull(log.getRedisAvailable());
assertNotNull(log.getDbStock());
assertNotNull(log.getDifference());
assertNotNull(log.getAction());
```

**实际结果：** 待验证

**证据：** 待提供数据库截图

---

## 性能测试

### 对账耗时

| 场景 | SKU 数量 | 预占记录数 | 耗时 |
|------|---------|-----------|------|
| 小规模 | 10 | 100 | 待测 |
| 中规模 | 100 | 1000 | 待测 |
| 大规模 | 1000 | 10000 | 待测 |

**测试方法：** 记录对账任务开始和结束时间

**证据：** 待提供性能测试报告

---

## 观察期数据

### 7 天观察（生产环境）

| 日期 | 对账次数 | 发现差异 | 自动修正 | 告警次数 | 清理孤儿 |
|------|---------|---------|---------|---------|---------|
| Day 1 | 待记录 | 待记录 | 待记录 | 待记录 | 待记录 |
| Day 2 | 待记录 | 待记录 | 待记录 | 待记录 | 待记录 |
| ... | ... | ... | ... | ... | ... |

**数据来源：** `inventory_reconciliation_log` 表统计

---

## 未覆盖场景

1. **极大规模**：10000+ SKU 的对账耗时未测试
2. **并发对账**：多实例同时对账的冲突未验证
3. **对账期间下单**：对账修正 Redis 时有订单提交的一致性未充分测试

---

## 验证结论

待完成实施后更新
