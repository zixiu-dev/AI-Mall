package com.bitmall.agent.aftersales.dao.repository;

import com.bitmall.agent.aftersales.dao.mapper.AfterSalesMapper;
import com.bitmall.agent.aftersales.vo.AfterSalesVO;
import com.bitmall.agent.common.persistence.RowValues;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 售后表的数据访问组件。 只负责按用户、订单或售后编号读写 after_sales，资格和退款金额由 AfterSalesService 决定。 */
@Repository
public class AfterSalesRepository {
  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final AfterSalesMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AfterSalesRepository(AfterSalesMapper mapper) {
    this.mapper = mapper;
  }

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
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
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
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
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
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
