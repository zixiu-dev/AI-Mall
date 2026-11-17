package com.bitmall.agent.order.inventory;

import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.service.OrderSubmissionPolicy;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Lua 前独立提交恢复意图，即使后续受理回滚，仍能按购买快照释放遗留的部分预占。 */
@Service
public class InventoryIntentService {
  private final OrderTraceRepository traces;
  private final OrderInventoryMapper mapper;
  private final InventoryReservationProperties properties;
  private final OrderSubmissionPolicy policy;

  public InventoryIntentService(
      OrderTraceRepository traces,
      OrderInventoryMapper mapper,
      InventoryReservationProperties properties,
      OrderSubmissionPolicy policy) {
    this.policy = policy;
    this.traces = traces;
    this.mapper = mapper;
    this.properties = properties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String prepare(long user, String id) {
    var t = traces.lock(id);
    if (t.getUserId() != user) throw new IllegalArgumentException("购买记录不属于当前用户");
    if (!"INIT".equals(t.getStatus())) return t.getMode();
    if (!t.getExpiresAt().isAfter(LocalDateTime.now()))
      throw new OrderRejectedException("EXPIRED", "确认已过期，请重新结算");
    String mode = t.getMode();
    if ("NONE".equals(t.getReserveStatus())) {
      mode = policy.firstSubmissionMode(mode);
      mapper.route(Map.of("id", id, "mode", mode));
      mapper.intent(
          Map.of(
              "id",
              id,
              "next",
              LocalDateTime.now().plusSeconds(properties.getAdmissionGraceSeconds())));
    }
    return mode;
  }
}
