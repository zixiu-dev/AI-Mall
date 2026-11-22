package com.bitmall.agent.order.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** 提交后库存整理使用独立有界线程池，避免占着原事务连接再等待新连接。 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@ConditionalOnProperty(name = "app.inventory.enabled", havingValue = "true")
public class InventoryReservationConfiguration {
  @Bean("inventorySettlementExecutor")
  public ThreadPoolTaskExecutor executor() {
    var e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(1);
    e.setMaxPoolSize(2);
    e.setQueueCapacity(64);
    e.setThreadNamePrefix("inventory-settle-");
    e.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    return e;
  }
}
