package com.bitmall.agent.order.config;

import com.bitmall.agent.common.persistence.RedissonSupport;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.*;

/** 交易 Redis 的统一 Redisson 连接，接口锁、库存与压力协调共用；MQ 去重保持原独立适配。 */
@Configuration(proxyBeanMethods = false)
public class OrderLockConfiguration {
  /** 延迟连接，关闭库存预扣和锁的离线 demo 无需交易 Redis 在线。 */
  @Bean(destroyMethod = "shutdown")
  @Lazy
  public RedissonClient orderRedisson(OrderProcessingProperties properties) {
    return RedissonSupport.connect(properties.getRedisHost(), properties.getRedisPort(),
        properties.getRedisPassword(), properties.getRedisDatabase(), 1000, 1000);
  }
}
