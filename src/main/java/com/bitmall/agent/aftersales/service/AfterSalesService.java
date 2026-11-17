package com.bitmall.agent.aftersales.service;

import com.bitmall.agent.aftersales.dao.repository.AfterSalesRepository;
import com.bitmall.agent.aftersales.model.SubmitAfterSalesCommand;
import com.bitmall.agent.aftersales.vo.AfterSalesVO;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.order.vo.OrderDetailVO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 售后业务服务。 先通过订单查询检查归属和状态，再判断是否可申请、金额是否变化；实际提交和仅生成预览是两个不同步骤。 */
@Service
public class AfterSalesService {
  /** 依赖组件：售后表的数据访问组件。 */
  private final AfterSalesRepository mapper;

  /** 依赖组件：订单交易业务服务。 */
  private final OrderService orders;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AfterSalesService(AfterSalesRepository mapper, OrderService orders) {
    this.mapper = mapper;
    this.orders = orders;
  }

  /** 先查询并核对订单归属，再检查状态和是否已有申请，返回可退款金额及原因。 */
  public Map<String, Object> afterSalesPrecheck(long userId, String orderId, String type) {
    OrderDetailVO order = orders.order(userId, orderId);
    boolean eligible =
        List.of("SHIPPED", "COMPLETED").contains(order.getStatus())
            && mapper.findAfterSalesByOrder(userId, orderId).isEmpty();
    String reason = eligible ? "订单属于当前用户且处于可申请售后状态" : "订单状态不允许申请，或已经存在售后单";
    return Map.of(
        "eligible",
        eligible,
        "orderId",
        orderId,
        "type",
        type,
        "orderStatus",
        order.getStatus(),
        "refundableAmount",
        order.getPayAmount(),
        "reason",
        reason);
  }

  /** 重新核对资格和确认金额，通过后才在事务中插入售后申请。 */
  @Transactional
  public Map<String, Object> submitAfterSales(long userId, SubmitAfterSalesCommand command) {
    String orderId = command.getOrderId();
    String type = command.getType();
    String reason = command.getReason();
    Map<String, Object> check = afterSalesPrecheck(userId, orderId, type);
    if (!Boolean.TRUE.equals(check.get("eligible"))) {
      throw new IllegalStateException(check.get("reason").toString());
    }
    BigDecimal expected = command.getExpectedRefundAmount();
    if (expected == null) throw new IllegalArgumentException("缺少参数 expectedRefundAmount");
    BigDecimal actual = new BigDecimal(check.get("refundableAmount").toString());
    if (expected.compareTo(actual) != 0) {
      throw new IllegalStateException("可退款金额已经变化，请重新确认");
    }
    String id = "AS" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    mapper.createAfterSales(id, userId, orderId, type, reason, actual);
    return Map.of(
        "success",
        true,
        "afterSalesId",
        id,
        "orderId",
        orderId,
        "status",
        "REVIEWING",
        "refundAmount",
        actual,
        "message",
        "售后申请已提交，等待审核");
  }

  /** 按售后单号或订单号查询当前用户的申请，缺失时明确返回业务错误。 */
  public AfterSalesVO afterSales(long userId, String id, String orderId) {
    if (id != null && !id.isBlank()) {
      return mapper
          .findAfterSales(userId, id)
          .orElseThrow(() -> new IllegalArgumentException("售后单不存在"));
    }
    return mapper
        .findAfterSalesByOrder(userId, orderId)
        .orElseThrow(() -> new IllegalArgumentException("该订单暂无售后记录"));
  }

  /** 查询真实订单并生成确认卡说明；这里只预览，不插入售后申请。 */
  public String previewAfterSales(long userId, SubmitAfterSalesCommand command) {
    Map<String, Object> check = afterSalesPrecheck(userId, command.getOrderId(), command.getType());
    return "提交"
        + command.getType()
        + "申请：订单 "
        + command.getOrderId()
        + "，预计退款 ¥"
        + check.get("refundableAmount")
        + "，原因："
        + command.getReason();
  }
}
