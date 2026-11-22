package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RedissonSupport;
import com.bitmall.agent.order.config.OrderProcessingProperties;
import com.bitmall.agent.order.exception.OrderRejectedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Redis 交易库存的读写入口，所有写操作都通过 resources/lua 下的脚本原子执行。
 *
 * <p>每个 SKU 有两类 key，都用 {skuId} 作为 hash tag 放在同一个 Redis Cluster 槽里，脚本才能同时操作：
 *
 * <ul>
 *   <li>{@code ...{sku}:available}：可售数量；
 *   <li>{@code ...{sku}:reservation:<reservationId>}：一次购买的预占记录（hash：quantity、state）。
 * </ul>
 *
 * <p>reservationId 通常就是购买 trace 的 traceId；Redis 预占上线前的旧订单补登时，使用由订单号派生的编号。
 *
 * <p>两类 key 都不设过期时间，预占记录由后台整理任务显式确认或释放。
 */
@Component
public class InventoryRedisStore {
  /** inventory_reserve.lua：本次新预占成功。 */
  private static final long RESERVE_CREATED = 1;

  /** inventory_reserve.lua：同一 trace 已经预占过（RESERVED/CONFIRMED），重复调用视为成功。 */
  private static final long RESERVE_ALREADY_HELD = 2;

  /** inventory_reserve.lua：可售库存不足。 */
  private static final long RESERVE_OUT_OF_STOCK = 0;

  /** inventory_reserve.lua：这次预占已经被释放或退回，不能再用。 */
  private static final long RESERVE_CLOSED = -3;

  private static final String STATE_RESERVED = "RESERVED";

  /** 交易专用 Redisson 客户端（orderRedisson），首次使用时才建立连接；执行库存 Lua 脚本。 */
  private final ObjectProvider<RedissonClient> client;

  private final String prefix;
  private final String reserveScript = RedissonSupport.script("inventory_reserve.lua");
  private final String finishScript = RedissonSupport.script("inventory_finish.lua");
  private final String initializeScript = RedissonSupport.script("inventory_initialize.lua");
  private final String adoptScript = RedissonSupport.script("inventory_adopt.lua");

  public InventoryRedisStore(
      @Qualifier("orderRedisson") ObjectProvider<RedissonClient> client,
      OrderProcessingProperties properties) {
    this.client = client;
    this.prefix = properties.getNamespace() + "inventory:v1:";
  }

  public String availableKey(long sku) {
    return skuPrefix(sku) + "available";
  }

  public String reservationKey(long sku, String reservationId) {
    return skuPrefix(sku) + "reservation:" + reservationId;
  }

  /**
   * 批量读取商品展示用的可售量。
   *
   * <p>Redis 里没有库存池的 SKU 不出现在返回结果中，由调用方再去数据库确认该 SKU 是否已初始化。
   */
  public Map<Long, Integer> availableForDisplay(List<Long> skuIds) {
    if (skuIds.isEmpty()) {
      return Map.of();
    }
    String[] keys = skuIds.stream().map(this::availableKey).toArray(String[]::new);
    Map<String, String> values = client.getObject().getBuckets(StringCodec.INSTANCE).get(keys);
    Map<Long, Integer> result = new HashMap<>();
    for (long skuId : skuIds) {
      String value = values.get(availableKey(skuId));
      if (value == null) {
        continue;
      }
      int available = Integer.parseInt(value);
      if (available < 0) {
        throw new IllegalStateException("库存池状态不完整");
      }
      result.put(skuId, available);
    }
    return result;
  }

  public boolean poolExists(long sku) {
    return Boolean.TRUE.equals(
        client.getObject().getBucket(availableKey(sku), StringCodec.INSTANCE).isExists());
  }

  /** 用数据库库存初始化 Redis 可售量。已存在且数值相同视为成功；已存在但数值不同说明两边账不一致，必须停流人工核对。 */
  public void initialize(long sku, int available) {
    Long code = execute(initializeScript, List.of(availableKey(sku)), String.valueOf(available));
    if (failed(code)) {
      throw new IllegalStateException("库存初始化状态不一致，请停流核对 SKU " + sku);
    }
  }

  /**
   * 为一次购买预占库存：扣减可售量并写入 RESERVED 记录。同一 trace 重复预占是幂等的。
   *
   * @throws OrderRejectedException 库存不足（OUT_OF_STOCK）或这次预占已结束（RESERVATION_CLOSED），可以直接告诉用户
   * @throws IllegalStateException 参数不合法、数量与已有记录不符、库存池缺失等需要排查的状态
   */
  public void reserve(long sku, String reservationId, int quantity) {
    Long code =
        execute(reserveScript, reservationKeys(sku, reservationId), String.valueOf(quantity));
    if (isCode(code, RESERVE_CREATED) || isCode(code, RESERVE_ALREADY_HELD)) {
      return;
    }
    if (isCode(code, RESERVE_OUT_OF_STOCK)) {
      throw new OrderRejectedException("OUT_OF_STOCK", "该规格可售库存不足");
    }
    if (isCode(code, RESERVE_CLOSED)) {
      throw new OrderRejectedException("RESERVATION_CLOSED", "本次库存预占已结束，请重新购买");
    }
    throw new IllegalStateException("库存预占状态异常，请核对 SKU " + sku);
  }

  /** 建单前确认这次购买确实持有数量一致的 RESERVED 预占，防止绕过预占直接建单。 */
  public void requireReserved(long sku, String reservationId, int quantity) {
    Map<String, String> reservation =
        client
            .getObject()
            .<String, String>getMap(reservationKey(sku, reservationId), StringCodec.INSTANCE)
            .readAllMap();
    boolean stillReserved = STATE_RESERVED.equals(reservation.get("state"));
    boolean quantityMatches = String.valueOf(quantity).equals(reservation.get("quantity"));
    if (!stillReserved || !quantityMatches) {
      throw new IllegalStateException("未找到匹配的有效预占，不能绕过预占建单");
    }
  }

  /** 整理一次预占：CONFIRM（订单成功，确认扣减）、RELEASE（建单失败，归还可售）、RETURN（订单关闭，归还可售）。 失败时抛异常，让整理任务保留并重试。 */
  public void finish(long sku, String reservationId, int quantity, String action) {
    Long code =
        execute(
            finishScript, reservationKeys(sku, reservationId), action, String.valueOf(quantity));
    if (failed(code)) {
      throw new IllegalStateException("库存整理尚未完成，保留任务重试：" + sku + " / " + action);
    }
  }

  /**
   * 为上线 Redis 预占之前创建的旧待支付订单补登一条 CONFIRMED 记录。 这些订单的库存当时已在数据库扣过，所以只登记、不再扣 Redis 可售量； 之后关单时才能正常走
   * RETURN 归还。
   */
  public void adopt(long sku, String reservationId, int quantity) {
    Long code = execute(adoptScript, reservationKeys(sku, reservationId), String.valueOf(quantity));
    if (failed(code)) {
      throw new IllegalStateException("旧订单库存登记失败，请核对 " + sku);
    }
  }

  private String skuPrefix(long sku) {
    return prefix + "{" + sku + "}:";
  }

  private List<String> reservationKeys(long sku, String reservationId) {
    return List.of(availableKey(sku), reservationKey(sku, reservationId));
  }

  private Long execute(String script, List<String> keys, Object... args) {
    return RedissonSupport.eval(client.getObject(), script, RScript.ReturnType.LONG, keys, args);
  }

  /** 脚本约定：返回负数表示失败；null 表示脚本没有正常返回。 */
  private static boolean failed(Long code) {
    return code == null || code < 0;
  }

  private static boolean isCode(Long code, long expected) {
    return code != null && code == expected;
  }
}
