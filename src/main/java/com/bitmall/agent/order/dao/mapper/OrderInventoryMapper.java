package com.bitmall.agent.order.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** 交易库存池初始化、预占意图及关单返还任务的数据库映射。 */
@Mapper
public interface OrderInventoryMapper {
  List<Map<String, Object>> sku(Map<String, Object> args);

  List<Map<String, Object>> lockSku(Map<String, Object> args);

  int initialized(Map<String, Object> args);

  int initializedCount();

  int route(Map<String, Object> args);

  int intent(Map<String, Object> args);

  int staleIntent(Map<String, Object> args);

  int admitted(Map<String, Object> args);

  int stage(Map<String, Object> args);

  List<Map<String, Object>> orderReservation(Map<String, Object> args);

  int insertReturn(Map<String, Object> args);

  List<Map<String, Object>> pendingReturns();

  List<Map<String, Object>> returnsForOrder(Map<String, Object> args);

  List<Map<String, Object>> lockReturn(Map<String, Object> args);

  int completedReturn(Map<String, Object> args);

  int returnPending(Map<String, Object> args);

  int returnCompleted(Map<String, Object> args);
}
