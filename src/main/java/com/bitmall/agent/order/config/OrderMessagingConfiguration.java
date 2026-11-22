package com.bitmall.agent.order.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 订单消息的启用入口。只有开启功能时才导入官方 MQ 装配，避免 demo 或离线测试意外连接远程服务。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.order-messaging.enabled", havingValue = "true")
@Import(RocketMQAutoConfiguration.class)
@EnableScheduling
@EnableAsync
public class OrderMessagingConfiguration {
  /** 只承接提交后发送优化；池满时丢掉唤醒通知，可靠消息仍在数据库，由扫描补发。 */
  @Bean("orderMessageDispatchExecutor")
  public ThreadPoolTaskExecutor
      orderMessageDispatchExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(64);
    executor.setThreadNamePrefix("order-outbox-");
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.DiscardPolicy());
    return executor;
  }
}
