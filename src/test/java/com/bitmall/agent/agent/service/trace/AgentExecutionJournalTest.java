package com.bitmall.agent.agent.service.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bitmall.agent.agent.dao.repository.AgentExecutionDetailRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** 队列和持久化失败留在诊断通道，下一批可继续写入。 */
class AgentExecutionJournalTest {
  @Test
  void fullQueueAndFailedBatchAreCountedWithoutThrowingOnChatThread() {
    var repository = mock(AgentExecutionDetailRepository.class);
    var metrics = new SimpleMeterRegistry();
    var journal = new AgentExecutionJournal(repository, metrics, 1, 7);
    try {
      doThrow(new IllegalStateException("write failed")).doNothing()
          .when(repository).writeItems(anyList());
      var trace = journal.startTurn(805);
      trace.beginQueueWait().finish("SUCCESS", null);
      trace.beginQueueWait().finish("SUCCESS", null);
      assertThat(metrics.get("agent.execution.detail.dropped").tag("reason", "queue_full")
          .counter().count()).isEqualTo(1);
      journal.flushBatch();
      assertThat(metrics.get("agent.execution.detail.dropped").tag("reason", "write_failed")
          .counter().count()).isEqualTo(1);

      trace.beginQueueWait().finish("SUCCESS", null);
      journal.flushBatch();
      verify(repository, times(2)).writeItems(anyList());
      assertThat(metrics.get("agent.execution.detail.queue.size").gauge().value()).isZero();
    } finally {
      journal.stop();
      metrics.close();
    }
  }
}
