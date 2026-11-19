# 增加库存对账机制

## 问题

当前设计缺少对账机制，可能导致 Redis 与 MySQL 长期不一致：

1. **Redis CONFIRM 失败**：订单成功但 Redis 未确认，状态卡在 CONFIRM_PENDING
2. **Redis RETURN 失败**：订单关闭但 Redis 未返还库存
3. **Redis 数据丢失**：可售量与 MySQL 不一致
4. **孤儿预占记录**：建单失败但 Redis 预占未清理

**影响：**
- Redis 库存不准确，可能超卖或少卖
- 预占记录永久保留，内存泄漏
- 没有自动修正机制

## 解决方案

增加三层对账机制：

### 1. 库存数量对账
- **对什么：** Redis available vs MySQL stock
- **频率：** 每小时
- **策略：** 小差异（≤5）自动修正，大差异告警

### 2. 预占记录对账
- **对什么：** Redis 预占记录 vs 实际订单
- **频率：** 每小时
- **策略：** 清理孤儿预占、清理过期终态记录

### 3. trace 状态对账
- **对什么：** 长时间 CONFIRM_PENDING/RETURN_PENDING
- **频率：** 每 5 分钟
- **策略：** 补执行 CONFIRM/RETURN

## 范围

**新增能力：**
- 定时对账任务
- 对账日志记录
- 差异告警

**影响的能力：**
- `inventory-reservation`：增加对账逻辑
- `observability`：增加对账指标

## 技术方案

### 对账任务

```java
@Service
public class InventoryReconciliationService {
    
    // 每小时对账
    @Scheduled(cron = "0 0 * * * ?")
    public void reconcile() {
        List<String> skus = getActiveSkus();
        
        for (String skuId : skus) {
            reconcileSku(skuId);
        }
    }
    
    private void reconcileSku(String skuId) {
        // 1. 库存数量对账
        reconcileStock(skuId);
        
        // 2. 预占记录对账
        reconcileReservations(skuId);
        
        // 3. trace 状态对账
        reconcileTraceStatus(skuId);
    }
}
```

### 1. 库存数量对账

```java
private void reconcileStock(String skuId) {
    // MySQL 权威
    Integer dbStock = skuMapper.selectStock(skuId);
    
    // Redis 实际
    Integer redisStock = redis.get("inventory:v1:{" + skuId + "}:available");
    
    if (redisStock == null) {
        alert("Redis 库存丢失", skuId);
        return;
    }
    
    int diff = Math.abs(redisStock - dbStock);
    
    if (diff > 5) {
        // 大差异：告警
        alert("库存差异过大", skuId, diff);
        logReconciliation(skuId, dbStock, redisStock, "ALERT");
    } else if (diff > 0) {
        // 小差异：自动修正
        redis.set("inventory:v1:{" + skuId + "}:available", dbStock);
        logReconciliation(skuId, dbStock, redisStock, "AUTO_FIX");
    }
}
```

### 2. 预占记录对账

```java
private void reconcileReservations(String skuId) {
    Set<String> keys = redis.keys("inventory:v1:{" + skuId + "}:reservation:*");
    
    for (String key : keys) {
        String traceId = extractTraceId(key);
        
        // 查询 trace
        Trace trace = traceMapper.selectById(traceId);
        
        if (trace == null) {
            // trace 不存在，检查预占创建时间（避免误删刚创建的）
            Long timestamp = redis.hget(key, "timestamp");
            if (timestamp != null && 
                System.currentTimeMillis() - timestamp > 300000) {
                // 超过 5 分钟的孤儿预占，删除
                redis.del(key);
                log.info("清理孤儿预占: traceId={}", traceId);
            }
            continue;
        }
        
        // 检查 trace 是否过期
        if (trace.getExpiresAt() != null && 
            trace.getExpiresAt().isBefore(LocalDateTime.now())) {
            redis.del(key);
            log.info("清理过期 trace 的预占: traceId={}", traceId);
            continue;
        }
        
        // 根据 trace 状态判断
        String status = trace.getStatus();
        if ("FAILED".equals(status)) {
            // 明确失败，删除
            redis.del(key);
            log.info("清理失败 trace 的预占: traceId={}", traceId);
        } else if ("INIT".equals(status) || "RESERVING".equals(status)) {
            // 检查是否超时（10 分钟）
            if (trace.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
                redis.del(key);
                log.info("清理超时 trace 的预占: traceId={}", traceId);
            }
        } else if ("PROCESSING".equals(status)) {
            // 异步处理中，检查 processing_expires_at
            if (trace.getProcessingExpiresAt() != null &&
                trace.getProcessingExpiresAt().isBefore(LocalDateTime.now())) {
                redis.del(key);
                log.info("清理异步超时预占: traceId={}", traceId);
            }
        } else if ("SUCCESS".equals(status) || "CLOSED".equals(status)) {
            // 终态，超过 7 天清理
            if (trace.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(7))) {
                redis.del(key);
                log.info("清理过期终态预占: traceId={}", traceId);
            }
        }
    }
}
```

### 3. trace 状态对账

```java
private void reconcileTraceStatus(String skuId) {
    // 补 CONFIRM
    List<Trace> pendingConfirm = traceMapper.selectConfirmPending(skuId);
    for (Trace trace : pendingConfirm) {
        inventorySettlement.settleLocked(trace);
    }
    
    // 补 RETURN
    List<InventoryReturn> pendingReturns = returnMapper.selectPending(skuId);
    for (InventoryReturn ret : pendingReturns) {
        inventoryOps.returnReservation(ret);
        returnMapper.markCompleted(ret.getId());
    }
}
```

### 对账日志表

```sql
CREATE TABLE inventory_reconciliation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id VARCHAR(64) NOT NULL,
    check_time DATETIME(6) NOT NULL,
    redis_available INT,
    db_stock INT,
    difference INT,
    action VARCHAR(20),  -- AUTO_FIX/ALERT/CLEARED
    details TEXT,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_sku_time (sku_id, check_time)
);
```

## 实施计划

1. 创建对账日志表
2. 实现对账服务
3. 配置定时任务
4. 增加监控指标
5. 配置告警规则
6. 更新文档

## 监控指标

- `inventory.reconciliation.diff_count`：差异数量
- `inventory.reconciliation.auto_fix_count`：自动修正次数
- `inventory.reconciliation.alert_count`：告警次数
- `inventory.reconciliation.orphan_cleared`：清理孤儿预占数

## 风险

- **低风险**：只读取和修正，不影响业务流程
- **可观测**：所有对账操作都有日志
- **可关闭**：配置开关控制
