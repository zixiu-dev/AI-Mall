package com.bitmall.agent.agent.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;
import static com.bitmall.agent.agent.gateway.model.ModelCircuitBreaker.Outcome.*;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 用可控时钟验证冷却、半开并发与迟到调用，无需真实等待。 */
class ModelCircuitBreakerTest {
  private final Duration cooldown = Duration.ofSeconds(30);
  private final AtomicLong clock = new AtomicLong();
  private final ModelCircuitBreaker circuit = new ModelCircuitBreaker(2, cooldown, clock::get);

  @Test
  void successfulCallClearsFailuresAndLateResultCannotReopenTraffic() {
    var late = circuit.acquire();
    circuit.complete(circuit.acquire(), FAILURE);
    circuit.complete(circuit.acquire(), SUCCESS);
    circuit.complete(circuit.acquire(), FAILURE);
    assertThat(circuit.state()).isEqualTo(ModelCircuitBreaker.State.CLOSED);

    circuit.complete(circuit.acquire(), FAILURE);
    circuit.complete(late, SUCCESS);
    assertThat(circuit.acquire()).isNull();

    clock.addAndGet(cooldown.toNanos());
    var probes = IntStream.range(0, 100).parallel().mapToObj(ignored -> circuit.acquire())
        .filter(Objects::nonNull).toList();
    assertThat(probes).hasSize(1);
    circuit.complete(late, FAILURE);
    assertThat(circuit.state()).isEqualTo(ModelCircuitBreaker.State.HALF_OPEN);
    circuit.complete(probes.get(0), SUCCESS);
    assertThat(circuit.state()).isEqualTo(ModelCircuitBreaker.State.CLOSED);
    assertThat(circuit.acquire()).isNotNull();
  }

  @Test
  void cancelledProbeReturnsSlotAndFailedProbeRestartsCooldown() {
    circuit.complete(circuit.acquire(), FAILURE);
    circuit.complete(circuit.acquire(), FAILURE);
    clock.addAndGet(cooldown.toNanos());

    var cancelled = circuit.acquire();
    circuit.complete(cancelled, NEUTRAL);
    var current = circuit.acquire();
    assertThat(current).isNotNull();
    circuit.complete(cancelled, SUCCESS);
    assertThat(circuit.acquire()).isNull();

    circuit.complete(current, FAILURE);
    clock.addAndGet(cooldown.toNanos() - 1);
    assertThat(circuit.acquire()).isNull();
    clock.incrementAndGet();
    assertThat(circuit.acquire()).isNotNull();
  }
}
