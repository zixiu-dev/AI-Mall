package com.bitmall.agent.aftersales.dao.repository;

import com.bitmall.agent.aftersales.dao.mapper.AfterSalesMapper;
import com.bitmall.agent.aftersales.vo.AfterSalesVO;
import com.bitmall.agent.common.persistence.RowValues;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 售后表的数据访问组件。 只负责按用户、订单或售后编号读写 after_sales，资格和退款金额由 AfterSalesService 决定。 */
@Repository
@RequiredArgsConstructor
public class AfterSalesRepository {
  /** 售后申请的 SQL，定义在 mapper/aftersales/AfterSalesMapper.xml。 */
  private final AfterSalesMapper mapper;

  /** 按用户编号和售后单号查询，确保只能读取自己的申请。 */
  public Optional<AfterSalesVO> findAfterSales(long userId, String afterSalesId) {
    return RowValues.optional(
        mapper.findAfterSalesSql(parameters("userId", userId, "id", afterSalesId)).stream()
            .map(
                row ->
                    new AfterSalesVO(
                        RowValues.string(row, "id"),
                        RowValues.string(row, "order_id"),
                        RowValues.string(row, "type"),
                        RowValues.string(row, "reason"),
                        RowValues.string(row, "status"),
                        RowValues.decimal(row, "refund_amount"),
                        RowValues.timestamp(row, "created_at").toLocalDateTime()))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 按当前用户和订单号查询最近一笔售后申请。 */
  public Optional<AfterSalesVO> findAfterSalesByOrder(long userId, String orderId) {
    return RowValues.optional(
        mapper.findAfterSalesByOrderSql(parameters("userId", userId, "orderId", orderId)).stream()
            .map(
                row ->
                    new AfterSalesVO(
                        RowValues.string(row, "id"),
                        RowValues.string(row, "order_id"),
                        RowValues.string(row, "type"),
                        RowValues.string(row, "reason"),
                        RowValues.string(row, "status"),
                        RowValues.decimal(row, "refund_amount"),
                        RowValues.timestamp(row, "created_at").toLocalDateTime()))
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 把已经由服务层核对过的售后参数插入数据库，初始状态为审核中。 */
  public String createAfterSales(
      String id, long userId, String orderId, String type, String reason, BigDecimal refundAmount) {
    mapper.createAfterSalesSql(
        parameters(
            "id",
            id,
            "orderId",
            orderId,
            "userId",
            userId,
            "type",
            type,
            "reason",
            reason,
            "refundAmount",
            refundAmount));
    return id;
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
