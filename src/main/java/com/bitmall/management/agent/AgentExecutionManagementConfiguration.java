package com.bitmall.management.agent;

import com.bitmall.agent.agent.service.chat.AgentTurnService;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;

/** 位于商城组件扫描范围之外，仅在独立管理端口的子上下文注册。 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class AgentExecutionManagementConfiguration {
  @Bean
  AgentExecutionDashboardController agentExecutionDashboardController(AgentTurnService turns) {
    return new AgentExecutionDashboardController(turns);
  }
}
