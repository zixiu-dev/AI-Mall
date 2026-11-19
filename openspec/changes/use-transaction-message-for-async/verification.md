# 验证记录

## 验证环境

- RocketMQ: 5.1.0
- Spring Boot: 4.1.2
- RocketMQ Spring Boot Starter: 2.3.0

---

## 需求验证

### MSG-001: 本地事务与消息原子

#### Scenario: 使用事务消息发送

**测试方法：**
```java
// 发送事务消息
admitAsync(traceId);

// 验证
1. 检查 trace.status = PROCESSING
2. 检查消息已投递到 Broker
3. 检查消费者收到消息
```

**实际结果：** 待验证

**证据：** 待提供测试日志

---

#### Scenario: 本地事务失败不发送

**测试方法：**
```java
// 模拟异常
Mockito.when(traceMapper.update(...)).thenThrow(new RuntimeException());

// 发送
admitAsync(traceId);

// 验证
1. trace 未更新
2. 消息未投递
3. 消费者未收到消息
```

**实际结果：** 待验证

**证据：** 待提供

---

### SUBM-004: 异步可靠受理

#### Scenario: 异步受理使用事务消息

**测试方法：**
```java
// 提交异步订单
OrderSubmitVO result = orderSubmissionService.submitAsync(request);

// 验证
1. result.status = PROCESSING
2. trace.status = PROCESSING
3. order_message_outbox 表无 ORDER_BUILD 记录
4. RocketMQ 有消息
5. 消息延迟 < 100ms
```

**实际结果：** 待验证

**证据：** 待提供

---

#### Scenario: 回查机制

**测试方法：**
```java
// 1. 发送事务消息但不响应
producer.sendMessageInTransaction(msg, traceId);
// 模拟网络中断，不返回 COMMIT/ROLLBACK

// 2. 触发回查
Thread.sleep(65000);  // 超过 60 秒，触发回查

// 3. 验证回查结果
// - trace 存在 → COMMIT
// - trace 不存在 → ROLLBACK
```

**实际结果：** 待验证

**证据：** 待提供

---

## 性能对比

### 消息延迟

| 指标 | Outbox | 事务消息 | 提升 |
|------|--------|---------|------|
| P50 延迟 | 待测 | 待测 | 待测 |
| P99 延迟 | 待测 | 待测 | 待测 |
| 数据库写入 | 2 次 | 1 次 | -50% |

**测试方法：** 压测 1000 笔订单，记录端到端延迟

**证据：** 待提供压测报告

---

## 未覆盖场景

1. **RocketMQ Broker 故障恢复**：需要真实环境验证
2. **高频回查场景**：需要观察生产环境回查频率
3. **大规模并发**：单机压测无法模拟真实负载

---

## 验证结论

待完成实施后更新
