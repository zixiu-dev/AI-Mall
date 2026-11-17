package com.bitmall.agent.order.service;

import com.bitmall.agent.common.idempotent.Idempotent;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.dao.entity.OrderTraceDO;
import com.bitmall.agent.order.dao.repository.OrderTraceRepository;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.vo.OrderSubmitVO;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 后端发号、保存购买快照和返回进度。生成编号不会预扣库存，同参数的新购买可生成新的 trace。 */
@Service
public class OrderTraceService {
  /** trace 持久化组件。 */
  private final OrderTraceRepository traces;

  /** 后端报价和 JSON 快照工具。 */
  private final OrderSnapshotService snapshots;

  /** 未提交凭证的有效期配置。 */
  private final OrderProcessingProperties properties;

  /** 只由后端配置选择执行路径。 */
  private final OrderSubmissionPolicy policy;

  /** 注入发号前需要的业务组件。 */
  public OrderTraceService(
      OrderTraceRepository traces,
      OrderSnapshotService snapshots,
      OrderProcessingProperties properties,
      OrderSubmissionPolicy policy) {
    this.traces = traces;
    this.snapshots = snapshots;
    this.properties = properties;
    this.policy = policy;
  }

  /** 准备确认；origin 只由后端给 Agent 操作绑定，前端无权指定其他来源编号。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Idempotent(scene = "ORDER_PREPARE")
  public OrderSubmitVO prepare(long user, CreateOrderCommand command, String origin) {
    if (origin != null) {
      var old = traces.findOrigin(origin);
      if (old.isPresent()) return view(owned(old.get(), user));
    }
    var quote = snapshots.prepare(user, command);
    String payload = snapshots.write(quote), id = UUID.randomUUID().toString();
    traces.insert(
        id,
        user,
        origin,
        policy.prepareMode(),
        payload,
        snapshots.hash(payload),
        LocalDateTime.now().plusSeconds(properties.getTraceTtlSeconds()),
        snapshots.commandHash(command));
    return get(user, id);
  }

  /** 按已校验用户读取自己的 trace，不返回内部购物快照。 */
  public OrderSubmitVO get(long user, String id) {
    return view(
        owned(traces.find(id).orElseThrow(() -> new IllegalArgumentException("下单任务不存在")), user));
  }

  /** Agent 确认使用前一步已经绑定的任务，不能临时拼接其他购买内容。 */
  public String originTrace(long user, String origin) {
    return owned(
            traces.findOrigin(origin).orElseThrow(() -> new IllegalStateException("下单确认已过期，请重新发起")),
            user)
        .getTraceId();
  }

  /** 用户检查必须在任何结果返回前完成。 */
  public OrderTraceDO owned(OrderTraceDO trace, long user) {
    if (trace.getUserId() != user) throw new IllegalArgumentException("下单任务不存在或不属于当前用户");
    return trace;
  }

  /** 从持久化终态组装页面响应；SUCCESS 才包含真实订单编号。 */
  public OrderSubmitVO view(OrderTraceDO trace) {
    String message =
        switch (trace.getStatus()) {
          case "INIT" -> "请核对购买内容后确认提交";
          case "PROCESSING" ->
              ("NONE".equals(trace.getReserveStatus())
                  ? "购买已进入处理队列，请使用原编号查询"
                  : "库存已预占，正在创建订单，请使用原编号查询");
          case "SUCCESS" -> "订单已创建，请查看订单当前支付状态";
          default -> trace.getFailureReason() == null ? "本次下单已结束" : trace.getFailureReason();
        };
    return new OrderSubmitVO(
        trace.getTraceId(),
        trace.getStatus(),
        trace.getMode(),
        trace.getOrderId(),
        snapshots.read(trace.getRequestSnapshot()).getPayAmount(),
        trace.getFailureCode(),
        message);
  }
}
