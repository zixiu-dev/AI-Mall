package com.bitmall.agent.order.idempotent;

import com.bitmall.agent.order.config.OrderProcessingProperties;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** MQ 消费去重保留的旧连接适配。库存与路由已迁到 Redisson，本类仅服务消费标记及兼容测试读取。 */
@Component
public class OrderRedisClient implements DisposableBean {
  /** 仅用于库存、预占和消费去重的连接工厂。 */
  private final LettuceConnectionFactory factory;

  /** 读写交易 Redis 的消息去重标记，并执行相关原子操作。 */
  private final StringRedisTemplate template;

  /** 创建客户端对象；实际命令执行前不会要求 Redis 必须可用。 */
  public OrderRedisClient(OrderProcessingProperties properties) {
    var config =
        new RedisStandaloneConfiguration(properties.getRedisHost(), properties.getRedisPort());
    config.setDatabase(properties.getRedisDatabase());
    if (!properties.getRedisPassword().isBlank()) {
      config.setPassword(properties.getRedisPassword());
    }
    factory =
        new LettuceConnectionFactory(
            config,
            LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ofMillis(100))
                .build());
    factory.afterPropertiesSet();
    template = new StringRedisTemplate(factory);
    template.afterPropertiesSet();
  }

  /** 返回独立模板，调用方只能操作自己的命名空间。 */
  public StringRedisTemplate template() {
    return template;
  }

  /** 停止独立连接工厂，避免测试或应用关闭后遗留线程。 */
  @Override
  public void destroy() {
    factory.destroy();
  }
}
