package com.bitmall.agent.common.monitor;

import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 管理端口读取原始一秒桶；只有显式启用逐秒统计时才装配，不作为商城业务接口。 */
@Component
@Endpoint(id = "throughput")
@ConditionalOnProperty(name = "app.throughput.enabled", havingValue = "true")
public class ThroughputEndpoint {
  private final OneSecondThroughput metrics;

  public ThroughputEndpoint(OneSecondThroughput metrics) {
    this.metrics = metrics;
  }

  @ReadOperation
  public Map<String, Object> status() {
    return metrics.snapshot();
  }
}
