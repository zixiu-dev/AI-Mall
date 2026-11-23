package com.bitmall.agent.agent.service.action;

import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.dao.repository.PendingActionRepository;
import com.bitmall.agent.agent.service.tool.RegisteredTool;
import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.bitmall.agent.agent.service.tool.support.ToolArguments;
import com.bitmall.agent.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.TreeMap;

/** 管理待确认操作。 生成稳定操作编号和参数摘要、保存确认卡数据、核对参数完整性，并协调执行成功或失败的状态更新。 */
@Service
public class PendingActionService {
  /** 创建下单确认卡时，同时冻结这次购买的快照（来源键为 agent:<确认卡ID>）；确认前不扣库存。 */
  private final OrderService orders;

  /** 读写确认卡及冻结参数，按当前状态更新批准、取消或执行结果。 */
  private final PendingActionRepository mapper;

  /** 在注入的 ObjectMapper 基础上开启按键排序，把操作参数序列化成稳定 JSON，用于计算参数摘要、确认时校验参数未被篡改。 */
  private final ObjectMapper canonicalMapper;

  /** 保存数据访问组件，并复制 JSON 配置以固定参数键顺序，为后续幂等计算做准备。 */
  public PendingActionService(
      PendingActionRepository mapper,
      ObjectMapper objectMapper,
      OrderService orders) {
    this.mapper = mapper;
    this.orders = orders;
    this.canonicalMapper =
        objectMapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
  }

  /** 先规范化参数、生成幂等键和操作摘要，再保存待确认记录；这一阶段不提交商城交易。 */
  @Transactional
  public PendingActionDO create(
      ToolExecutionContext context, RegisteredTool tool, Map<String, Object> arguments) {
    // 参数先统一键顺序和数字格式；同一业务参数不会因 JSON 表示不同而变成另一笔提议。
    String canonical = canonical(arguments);
    String argumentsHash = sha256(canonical);
    String idempotencyKey = sha256(context.getRequestId() + "|" + tool.getName() + "|" + canonical);
    String id =
        UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "");
    // 预览会查询真实商品/订单以生成摘要，但不会建单、扣库存或领券。
    String summary = tool.getPreview().describe(context, arguments);
    PendingActionDO action =
        new PendingActionDO(
            id,
            idempotencyKey,
            context.getUserId(),
            context.getConversationId(),
            tool.getName(),
            Map.copyOf(arguments),
            argumentsHash,
            summary,
            "PENDING",
            Map.of(),
            0,
            LocalDateTime.now());
    // 这里只保存“准备执行什么”；用户点击确认后才会触发真正业务。
    mapper.insert(action);
    if ("order_create".equals(tool.getName())) {
      orders.prepareAction(
          context.getUserId(),
          ToolArguments.orderCommand(arguments),
          id);
    }
    return mapper.find(id, context.getUserId()).orElseThrow();
  }

  /** 用户同意时重新计算参数摘要并核对完整性，然后条件更新确认状态。 */
  @Transactional
  public PendingActionDO decide(String id, long userId, boolean approved) {
    PendingActionDO action = find(id, userId);
    if (approved && !action.getArgumentsHash().equals(sha256(canonical(action.getArguments())))) {
      throw new IllegalStateException("确认参数完整性校验失败，请重新发起操作");
    }
    return mapper.decide(id, userId, approved);
  }

  /** 按操作和用户编号查询，拒绝访问不存在或不属于当前用户的操作。 */
  public PendingActionDO find(String id, long userId) {
    return mapper.find(id, userId).orElseThrow(() -> new IllegalArgumentException("操作不存在或不属于当前用户"));
  }

  /** 把业务成功结果交给 Mapper 按版本标记完成。 */
  public void executed(PendingActionDO action, Map<String, Object> result) {
    mapper.markExecuted(action.getId(), action.getVersion(), result);
  }

  /** 把执行失败结果交给 Mapper 按版本记录。 */
  public void failed(PendingActionDO action, Map<String, Object> result) {
    mapper.markFailed(action.getId(), action.getVersion(), result);
  }

  /** 读取会话最新待确认操作，没有时返回空供编排继续聊天。 */
  public PendingActionDO latest(String conversationId) {
    return mapper.latestPending(conversationId).orElse(null);
  }

  /** 返回该用户在本会话中近期已决定执行的下单操作编号。 */
  public List<String> recentDecidedOrderIds(long userId, String conversationId, int limit) {
    return mapper.recentDecidedOrderIds(userId, conversationId, limit);
  }

  /** 把规范化参数序列化为稳定 JSON，确保键顺序和数字表示不会影响幂等判断。 */
  private String canonical(Map<String, Object> arguments) {
    try {
      return canonicalMapper.writeValueAsString(normalize(arguments));
    } catch (Exception e) {
      throw new IllegalArgumentException("工具参数无法序列化", e);
    }
  }

  // JSON 落库可能把 4999.00 变成 4999.0，也可能改变键顺序。
  // 因此先统一数字和键顺序，再计算摘要，避免同一业务参数校验失败。
  /** 递归排序 Map 键并统一数字表示，让 4999.00 与 4999.0 得到相同业务摘要。 */
  private Object normalize(Object value) {
    if (value instanceof Number n) {
      return new BigDecimal(n.toString()).stripTrailingZeros();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(key.toString(), normalize(item)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::normalize).toList();
    }
    return value;
  }

  /** 计算文本摘要，用于稳定操作编号、幂等键和参数完整性校验。 */
  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("无法计算参数摘要", e);
    }
  }
}
