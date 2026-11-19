package com.bitmall.agent.order.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** 验证启动未知状态、成功采样和数据库故障时的快照边界。 */
class OrderBuildBacklogMonitorTest {
  @Test
  void databaseFailureKeepsLastSuccessfulSampleWithoutReportingAnEmptyBacklog() {
    var traces = mock(OrderTraceRepository.class);
    var registry = new SimpleMeterRegistry();
    var monitor = new OrderBuildBacklogMonitor(traces, registry);
    var pending = registry.get("mall.order.build.pending").tag("stage", "processing").gauge();
    assertThat(pending.value()).isNaN();
    assertThat(monitor.status()).containsEntry("fresh", false);

    when(traces.asyncBuildBacklog())
        .thenReturn(List.of(Map.of(
            "stage", "processing", "pending", 3L, "oldest", LocalDateTime.now().minusSeconds(65))))
        .thenThrow(new DataAccessResourceFailureException("数据库采样失败"));
    monitor.sample();
    var successful = monitor.status();
    assertThat(successful).containsEntry("fresh", true);
    assertThat(pending.value()).isEqualTo(3);
    assertThat(registry.get("mall.order.build.oldest.wait.seconds")
        .tag("stage", "processing").gauge().value()).isGreaterThanOrEqualTo(65);

    monitor.sample();
    assertThat(monitor.status().get("sampledAtEpochSeconds"))
        .isEqualTo(successful.get("sampledAtEpochSeconds"));
    assertThat(monitor.status().get("stages")).isEqualTo(successful.get("stages"));
    assertThat(pending.value()).isEqualTo(3);
  }
}
