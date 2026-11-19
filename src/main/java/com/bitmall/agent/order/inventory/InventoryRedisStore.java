package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.exception.OrderRejectedException;
import java.util.*;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 交易库存的 Lua 适配器。SKU 可售数和单次预占记录同槽，均不自动过期。 */
@Component
public class InventoryRedisStore {
  private final ObjectProvider<RedissonClient> client;
  private final String prefix;
  private final String reserve = script("inventory_reserve.lua");
  private final String finish = script("inventory_finish.lua");
  private final String initialize = script("inventory_initialize.lua");
  private final String adopt = script("inventory_adopt.lua");

  public InventoryRedisStore(
      @Qualifier("orderRedisson") ObjectProvider<RedissonClient> client,
      OrderProcessingProperties properties) {
    this.client = client;
    prefix = properties.getNamespace() + "inventory:v1:";
  }

  private static String script(String name) {
    return RedissonSupport.script(name);
  }

  /** 同一 SKU 的库存操作通过 Lua 原子执行。 */
  private Long execute(String script, List<String> keys, Object... args) {
    return RedissonSupport.eval(client.getObject(), script, RScript.ReturnType.LONG, keys, args);
  }

  private String skuPrefix(long sku) {
    return prefix + "{" + sku + "}:";
  }

  public String availableKey(long sku) {
    return skuPrefix(sku) + "available";
  }

  public String reservationKey(long sku, String id) {
    return skuPrefix(sku) + "reservation:" + id;
  }

  private List<String> reservationKeys(long sku, String id) {
    return List.of(availableKey(sku), reservationKey(sku, id));
  }

  /** 浏览只批读可售量；缺失项由调用方核实数据库初始化标记。 */
  public Map<Long, Integer> availableForDisplay(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    String[] names = ids.stream().map(this::availableKey).toArray(String[]::new);
    Map<String, String> values = client.getObject().getBuckets(StringCodec.INSTANCE).get(names);
    var result = new HashMap<Long, Integer>();
    for (long id : ids) {
      String value = values.get(availableKey(id));
      if (value == null) continue;
      int count = Integer.parseInt(value);
      if (count < 0)
        throw new IllegalStateException("库存池状态不完整");
      result.put(id, count);
    }
    return result;
  }

  public boolean poolExists(long sku) {
    return Boolean.TRUE.equals(
        client.getObject().getBucket(availableKey(sku), StringCodec.INSTANCE).isExists());
  }

  public void initialize(long sku, int available) {
    Long n = execute(initialize, List.of(availableKey(sku)), String.valueOf(available));
    if (n == null || n < 0) throw new IllegalStateException("库存初始化状态不一致，请停流核对 SKU " + sku);
  }

  public void reserve(long sku, String id, int quantity) {
    Long n = execute(reserve, reservationKeys(sku, id), String.valueOf(quantity));
    if (Long.valueOf(1).equals(n) || Long.valueOf(2).equals(n)) return;
    if (Long.valueOf(0).equals(n)) throw new OrderRejectedException("OUT_OF_STOCK", "该规格可售库存不足");
    if (Long.valueOf(-3).equals(n))
      throw new OrderRejectedException("RESERVATION_CLOSED", "本次库存预占已结束，请重新购买");
    throw new IllegalStateException("库存预占状态异常，请核对 SKU " + sku);
  }

  public void requireReserved(long sku, String id, int quantity) {
    var h =
        client
            .getObject()
            .<String, String>getMap(reservationKey(sku, id), StringCodec.INSTANCE)
            .readAllMap();
    if (!"RESERVED".equals(h.get("state")) || !String.valueOf(quantity).equals(h.get("quantity")))
      throw new IllegalStateException("未找到匹配的有效预占，不能绕过预占建单");
  }

  public void finish(long sku, String id, int quantity, String action) {
    Long n = execute(finish, reservationKeys(sku, id), action, String.valueOf(quantity));
    if (n == null || n < 0)
      throw new IllegalStateException("库存整理尚未完成，保留任务重试：" + sku + " / " + action);
  }

  public void adopt(long sku, String id, int quantity) {
    Long n = execute(adopt, reservationKeys(sku, id), String.valueOf(quantity));
    if (n == null || n < 0) throw new IllegalStateException("旧订单库存登记失败，请核对 " + sku);
  }
}
