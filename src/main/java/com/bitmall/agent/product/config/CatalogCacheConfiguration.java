package com.bitmall.agent.product.config;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.product.cache.CatalogCache;
import com.bitmall.agent.product.cache.RedisCatalogStore;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.LoggerFactory;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

/** 缓存 Redis 的 Redisson 装配与 RTopic 失效订阅。连接延迟建立，断线后由客户端恢复订阅。 */
@Configuration
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
public class CatalogCacheConfiguration {
  /** 按需取得商品缓存专用的 Redisson 客户端，首次实际使用时才建立连接。 */
  @Qualifier("catalogRedisson")
  private final ObjectProvider<RedissonClient> clients;

  /** 按需取得商品 Redis 操作适配器，供缓存失效订阅使用。 */
  private final ObjectProvider<RedisCatalogStore> stores;

  /** 按需取得商品缓存服务，收到失效消息后清理本机缓存。 */
  private final ObjectProvider<CatalogCache> caches;

  /** 读取商品缓存开关；关闭时不订阅失效广播。 */
  private final CatalogCacheProperties properties;

  private RTopic subscribed;
  private Integer listenerId;

  /** 沿用原商品 Redis 地址；交易 Redis 使用另一个具名客户端，不共享淘汰策略。 */
  @Bean(destroyMethod = "shutdown")
  @Lazy
  RedissonClient catalogRedisson(
      @Value("${spring.data.redis.host:127.0.0.1}") String host,
      @Value("${spring.data.redis.port:6380}") int port,
      @Value("${spring.data.redis.password:}") String password,
      @Value("${spring.data.redis.database:0}") int database) {
    return RedissonSupport.connect(host, port, password, database, 300, 200);
  }

  @Bean("catalogMaintenanceExecutor")
  ThreadPoolTaskExecutor catalogMaintenanceExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("catalog-bloom-");
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.DiscardPolicy());
    return executor;
  }

  /** 首次订阅失败由本任务重试；订阅建立之后 Redisson 管理网络重连和重新订阅。 */
  @Scheduled(fixedDelay = 3000, initialDelay = 1000)
  public synchronized void ensureSubscription() {
    if (!properties.isEnabled() || listenerId != null) {
      return;
    }
    try {
      RTopic topic =
          clients.getObject().getTopic(stores.getObject().channel(), StringCodec.INSTANCE);
      listenerId =
          topic.addListener(
              String.class, (channel, key) -> caches.getObject().invalidateLocal(key));
      subscribed = topic;
    } catch (RuntimeException ex) {
      LoggerFactory.getLogger(getClass()).debug("Redisson 失效订阅暂不可用，稍后重试");
    }
  }

  @PreDestroy
  public synchronized void unsubscribe() {
    if (subscribed != null && listenerId != null) {
      try {
        subscribed.removeListener(listenerId);
      } catch (RuntimeException ignored) {
      }
    }
  }
}
