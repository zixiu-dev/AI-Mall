package com.bitmall.agent.common.idempotent;

/** 通用切面的存储边界。锁与消费标记由 Redis 实现，接口不依赖具体订单表。 */
public interface IdempotencyBackend {
    /** 尝试取得接口分布式锁；返回句柄由切面在事务结束后关闭。 */
    AutoCloseable lock(String key);

    /** 抢占 MQ 消费标记，返回令牌；DONE 表示已消费，DISABLED 表示仅使用数据库兜底。 */
    String begin(String key);

    /** 仅在本次令牌仍有效时标记消费完成。 */
    void complete(String key, String token);

    /** 失败时只删除自己的消费标记，不能删除其他消费者后来取得的标记。 */
    void failed(String key, String token);
}
