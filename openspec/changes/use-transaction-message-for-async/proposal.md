# 异步建单改用 RocketMQ 事务消息

## 问题

当前异步建单使用 Outbox 模式：

1. 本地事务写 `order_submit_trace` 和 `order_message_outbox` 两张表
2. 定时任务扫描 Outbox 发送 MQ（5 秒间隔）
3. 消息延迟 2-5 秒

**缺点：**
- 多写一张表（Outbox）
- 定时扫描有延迟
- 扫描任务持续占用数据库连接

## 解决方案

使用 RocketMQ 事务消息替代 Outbox：

1. 本地事务只写 `order_submit_trace` 表
2. RocketMQ 框架保证消息与本地事务一致性
3. 消息延迟降低到 50ms

**收益：**
- 减少一次数据库写入（-50%）
- 消息延迟降低 97%（5s → 50ms）
- 删除定时扫描任务
- 代码行数减少 40%

## 范围

**影响的能力：**
- `order-submission`：异步受理流程
- `order-messaging`：消息发送机制

**不影响：**
- 同步建单流程
- 订单事件消息（ORDER_CREATED/PAID/CANCELLED 保留 Outbox）
- 消费者逻辑（完全不变）

## 技术方案

### 改造前（Outbox）

```java
@Transactional
public void admitAsync(String traceId) {
    // 1. 更新 trace
    traceMapper.updateProcessing(traceId);
    
    // 2. 写 Outbox
    outboxMapper.insert(new Message("ORDER_BUILD", traceId));
    
    // COMMIT
}

// 定时扫描
@Scheduled(fixedDelay = 5000)
public void scan() {
    outboxMapper.selectPending().forEach(msg -> {
        mq.send(msg);
        outboxMapper.markSent(msg.getId());
    });
}
```

### 改造后（事务消息）

```java
// 发送事务消息
public void admitAsync(String traceId) {
    Message msg = new Message("ORDER_BUILD", traceId.getBytes());
    producer.sendMessageInTransaction(msg, traceId);
}

// 本地事务
@Override
@Transactional
public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
    String traceId = (String) arg;
    
    // 只更新 trace（不写 Outbox）
    traceMapper.updateProcessing(traceId);
    
    return LocalTransactionState.COMMIT_MESSAGE;
}

// 回查
@Override
public LocalTransactionState checkLocalTransaction(MessageExt msg) {
    String traceId = msg.getKeys();
    Trace trace = traceMapper.selectById(traceId);
    
    if (trace != null && "PROCESSING".equals(trace.getStatus())) {
        return LocalTransactionState.COMMIT_MESSAGE;
    }
    return LocalTransactionState.ROLLBACK_MESSAGE;
}

// 删除定时扫描任务
```

## 实施计划

1. 增加 TransactionMQProducer 配置
2. 实现 TransactionListener（本地事务 + 回查）
3. 改造 admitAsync 发送逻辑
4. 灰度验证（10% → 50% → 100%）
5. 删除 Outbox 扫描任务
6. 更新文档

## 兼容性

- 消费者完全不需要改动
- Outbox 表保留（用于订单事件）
- 可以灰度切换（配置开关控制）

## 风险

- **低风险**：只改发送端，消费端不变
- **可回滚**：保留 Outbox 逻辑，配置开关可切回
- **RocketMQ 依赖**：必须使用 RocketMQ（不能换 Kafka）
