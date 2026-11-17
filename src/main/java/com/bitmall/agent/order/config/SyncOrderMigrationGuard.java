package com.bitmall.agent.order.config;

import java.sql.SQLException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** 删除旧活动表前的升级检查，避免旧异步任务和 Redis 预占尚未结清就切到普通购买。 */
@Component
public class SyncOrderMigrationGuard implements Callback {
  @Override
  public boolean supports(Event event, Context context) {
    return event == Event.BEFORE_EACH_MIGRATE
        && context.getMigrationInfo() != null
        && "13".equals(context.getMigrationInfo().getVersion().getVersion());
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return true;
  }

  @Override
  public String getCallbackName() {
    return "check-legacy-async-orders-before-removing-activities";
  }

  /** 先用旧版本排空任务并完成预占确认/释放，再运行新版本；不自动销毁未完成购买。 */
  @Override
  public void handle(Event event, Context context) {
    try (var query = context.getConnection().createStatement();
        var result =
            query.executeQuery(
                "SELECT COUNT(*) FROM order_submit_trace WHERE mode='ASYNC' AND (status IN"
                    + " ('INIT','PROCESSING') OR reserve_status IN "
                    + "('RESERVING','RESERVED','CONFIRM_PENDING','RELEASE_PENDING','RETURN_PENDING'))")) {
      result.next();
      if (result.getLong(1) > 0)
        throw new IllegalStateException("仍有旧异步购买或库存补偿未完成：请先停止旧版本接单，排空任务并核对 Redis 预占后再升级；本次未删除活动表");
    } catch (SQLException ex) {
      throw new IllegalStateException("无法核对旧异步任务，暂停同步下单迁移", ex);
    }
  }
}
