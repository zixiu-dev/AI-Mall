package com.bitmall.agent.agent.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 聊天使用有界工作线程；计时线程只发送中断，不执行模型或业务操作。 */
@Configuration
public class AgentExecutionConfiguration {
  @Bean
  public ThreadPoolTaskExecutor agentChatExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(32);
    executor.setThreadNamePrefix("agent-chat-");
    executor.setWaitForTasksToCompleteOnShutdown(false);
    return executor;
  }

  /** 负责把 SSE 队列写入浏览器，隔离模型生成线程与慢客户端。 */
  @Bean
  public ThreadPoolTaskExecutor agentStreamSender() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(32);
    executor.setThreadNamePrefix("agent-stream-send-");
    return executor;
  }

  @Bean(destroyMethod = "shutdownNow")
  public ScheduledExecutorService agentDeadlineScheduler() {
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            1,
            task -> {
              Thread thread = new Thread(task, "agent-deadline");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
