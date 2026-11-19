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

/** 交易库存的 Lua 适配器。可售数、待落库预占量和单次预占记录同槽，均不自动过期。 */
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

  /** 保留同槽原子脚本，仅替换客户端。 */
  private Long execute(String script, List<String> keys, Object... args) {
    return RedissonSupport.eval(client.getObject(), script, RScript.ReturnType.LONG, keys, args);
  }

  public List<String> keys(long sku, String id) {
    String p = prefix + "{" + sku + "}:";
    return List.of(p + "available", p + "pending", p + "reservation:" + id);
  }

  /** 浏览只批读可售与待确认计数，不初始化或修复库存。两键均缺失由调用方核实 DB 初始化标记。 */
  public Map<Long, Integer> availableForDisplay(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var names =
        ids.stream()
            .flatMap(id -> keys(id, "unused").subList(0, 2).stream())
            .toArray(String[]::new);
    Map<String, String> values = client.getObject().getBuckets(StringCodec.INSTANCE).get(names);
    var result = new HashMap<Long, Integer>();
    for (long id : ids) {
      var pair = keys(id, "unused");
      String a = values.get(pair.get(0)), p = values.get(pair.get(1));
      if (a == null && p == null) continue;
      if (a == null || p == null || Integer.parseInt(a) < 0 || Integer.parseInt(p) < 0)
        throw new IllegalStateException("库存池状态不完整");
      result.put(id, Integer.parseInt(a));
    }
    return result;
  }

  public boolean poolExists(long sku) {
    var k = keys(sku, "unused");
    return Boolean.TRUE.equals(
            client.getObject().getBucket(k.get(0), StringCodec.INSTANCE).isExists())
        && Boolean.TRUE.equals(
            client.getObject().getBucket(k.get(1), StringCodec.INSTANCE).isExists());
  }

  public void initialize(long sku, int available) {
    Long n = execute(initialize, keys(sku, "unused").subList(0, 2), String.valueOf(available));
    if (n == null || n < 0) throw new IllegalStateException("库存初始化状态不一致，请停流核对 SKU " + sku);
  }

  public void reserve(long sku, String id, int quantity) {
    Long n = execute(reserve, keys(sku, id), String.valueOf(quantity));
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
            .<String, String>getMap(keys(sku, id).get(2), StringCodec.INSTANCE)
            .readAllMap();
    if (!"RESERVED".equals(h.get("state")) || !String.valueOf(quantity).equals(h.get("quantity")))
      throw new IllegalStateException("未找到匹配的有效预占，不能绕过预占建单");
  }

  public void finish(long sku, String id, int quantity, String action) {
    Long n = execute(finish, keys(sku, id), action, String.valueOf(quantity));
    if (n == null || n < 0)
      throw new IllegalStateException("库存整理尚未完成，保留任务重试：" + sku + " / " + action);
  }

  public void adopt(long sku, String id, int quantity) {
    Long n = execute(adopt, keys(sku, id), String.valueOf(quantity));
    if (n == null || n < 0) throw new IllegalStateException("旧订单库存登记失败，请核对 " + sku);
  }
}
