package com.bitmall.agent.product.service;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.inventory.InventoryRedisStore;
import com.bitmall.agent.product.cache.CacheMetrics;
import com.bitmall.agent.product.cache.CacheTtlPolicy;
import com.bitmall.agent.product.cache.CatalogCache;
import com.bitmall.agent.product.cache.ProductProfile;
import com.bitmall.agent.product.cache.RedisCatalogStore;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.bitmall.agent.product.vo.SkuVO;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 给商品详情补上每个 SKU 的展示价格和展示库存。
 *
 * <p>数据来源：
 *
 * <ul>
 *   <li>价格：商品 Redis 缓存 {@code sku:price:<id>}，值格式为 {@code 现价/原价}（无原价时斜杠后为空）；
 *   <li>库存：启用交易库存预占时，读交易 Redis 的可售量（只读）；未启用时，读商品 Redis 缓存 {@code sku:availability:<id>}。
 * </ul>
 *
 * <p>缓存缺失时，通过 {@link CatalogCache#loadOffers} 拿锁后批量回源数据库并回填。 这里的价格和库存只用于展示，下单时会重新核对，不能拿它们确认成交。
 */
@Service
@RequiredArgsConstructor
public class SkuDisplayService {
  private static final String PRICE_KEY_PREFIX = "sku:price:";
  private static final String STOCK_KEY_PREFIX = "sku:availability:";

  /** 价格缓存值中现价与原价的分隔符。 */
  private static final String PRICE_SEPARATOR = "/";

  /** 缓存缺失时，从数据库批量读取 SKU 的价格、库存和库存池是否已初始化。 */
  private final ProductRepository products;

  /** 缓存缺失时拿商品级回源锁，并与详情查询共用回源并发额度。 */
  private final CatalogCache cache;

  /** 批量读取和回填商品 Redis 缓存里的价格/库存，删除格式损坏的值。 */
  private final RedisCatalogStore store;

  /** 交易库存池（只读可售量）。 */
  private final InventoryRedisStore pools;

  /** 读取是否启用交易库存预占；启用时库存改从交易 Redis 库存池读取。 */
  private final InventoryReservationProperties inventory;

  /** 给缓存时长加随机抖动，避免一批 key 同时过期。 */
  private final CacheTtlPolicy ttl;

  /** 读取商品缓存总开关。 */
  private final CatalogCacheProperties config;

  /** 记录 SKU 报价批量回源数据库的次数。 */
  private final CacheMetrics metrics;

  /** SKU 展示价格在 Redis 中的基础缓存时长，默认 60 秒（实际写入时再加随机抖动）。 */
  @Value("${app.catalog-cache.price-ttl:60s}")
  private final Duration priceTtl;

  /** 库存变化快，缓存时间比价格短得多。 */
  @Value("${app.catalog-cache.stock-ttl:3s}")
  private final Duration stockTtl;

  public ProductDetailVO detail(ProductProfile profile) {
    List<Long> skuIds = profile.skus.stream().map(sku -> sku.id).toList();
    if (skuIds.isEmpty()) {
      return profile.withOffers(List.of());
    }
    boolean pooled = inventory.isEnabled();
    Map<Long, Integer> poolAvailable = readPoolAvailability(pooled, skuIds);
    List<String> cacheKeys = cacheKeys(pooled, skuIds);

    // 事务内不读写共享缓存：事务里看到的数据可能尚未提交，不能回填给其他请求。
    boolean shared =
        config.isEnabled() && !TransactionSynchronizationManager.isActualTransactionActive();

    // 1. 先直接读一次缓存，全部命中就不需要拿锁。
    Map<String, RedisCatalogStore.Lookup> firstReads =
        shared ? store.lookupMany(cacheKeys) : Map.of();
    Map<String, String> values = wellFormedValues(firstReads);

    // 2. 有缺失时拿锁，锁内重读缓存并把仍缺失的部分回源数据库。
    boolean cacheIncomplete = values.size() != cacheKeys.size();
    boolean poolIncomplete = pooled && poolAvailable.size() != skuIds.size();
    if (cacheIncomplete || poolIncomplete) {
      values =
          cache.loadOffers(
              profile.id,
              shared && store.available(),
              owner ->
                  loadMissingOffers(
                      profile.id, skuIds, cacheKeys, poolAvailable, pooled, shared, owner));
    }

    return profile.withOffers(toOffers(profile, values, poolAvailable));
  }

  /** 启用库存预占时读取交易 Redis 的可售量；交易 Redis 不可用时直接返回 503，不能退回读数据库库存。 */
  private Map<Long, Integer> readPoolAvailability(boolean pooled, List<Long> skuIds) {
    if (!pooled) {
      return Map.of();
    }
    try {
      return pools.availableForDisplay(skuIds);
    } catch (RuntimeException ex) {
      throw unavailable();
    }
  }

  /** 每个 SKU 都要价格缓存；只有不走库存池时，库存才走商品缓存。 */
  private static List<String> cacheKeys(boolean pooled, List<Long> skuIds) {
    List<String> keys = new ArrayList<>();
    for (long skuId : skuIds) {
      keys.add(priceKey(skuId));
      if (!pooled) {
        keys.add(stockKey(skuId));
      }
    }
    return keys;
  }

  /**
   * 拿到回源锁后执行：重读缓存，把仍缺失的价格/库存从数据库批量补齐，并回填商品缓存。
   *
   * @param owner 锁持有者标识；未拿到跨实例锁时为 null，此时 fillOffers 会跳过回填
   */
  private Map<String, String> loadMissingOffers(
      long productId,
      List<Long> skuIds,
      List<String> cacheKeys,
      Map<Long, Integer> poolAvailable,
      boolean pooled,
      boolean shared,
      String owner) {
    // 锁内重读：等锁期间别的实例可能已经回填；重读也会拿到本轮回填所需的新凭据。
    Map<String, RedisCatalogStore.Lookup> reads = shared ? store.lookupMany(cacheKeys) : Map.of();
    Map<String, String> found = wellFormedValues(reads);
    Map<String, String> updates = new HashMap<>();

    List<Long> missingSkuIds =
        skuIds.stream()
            .filter(
                skuId ->
                    !found.containsKey(priceKey(skuId))
                        || stockMissing(skuId, poolAvailable, found))
            .toList();
    if (!missingSkuIds.isEmpty()) {
      metrics.increment("sku_batch_db_load");
    }

    for (Map<String, Object> row : products.displayRows(missingSkuIds)) {
      long skuId = RowValues.longValue(row, "id");
      if (!found.containsKey(priceKey(skuId))) {
        updates.put(priceKey(skuId), encodePrice(row));
      }
      if (stockMissing(skuId, poolAvailable, found)) {
        // 库存池已经初始化过、现在却读不到，说明 Redis 数据丢了；此时数据库库存已经不准，不能拿来展示"有货"。
        // 只有从未初始化过的池，才允许临时展示数据库库存。
        if (pooled && RowValues.bool(row, "redis_inventory_initialized")) {
          throw unavailable();
        }
        String stock = String.valueOf(RowValues.intValue(row, "stock"));
        if (pooled) {
          // 库存池模式下数据库库存只临时展示，不回填到商品缓存。
          found.put(stockKey(skuId), stock);
        } else {
          updates.put(stockKey(skuId), stock);
        }
      }
    }

    if (shared) {
      store.fillOffers("offers:" + productId, owner, reads, updates, this::jitteredTtlMillis);
    }
    found.putAll(updates);
    return found;
  }

  private static boolean stockMissing(
      long skuId, Map<Long, Integer> poolAvailable, Map<String, String> cached) {
    return !poolAvailable.containsKey(skuId) && !cached.containsKey(stockKey(skuId));
  }

  /** 编码为价格缓存值：现价/原价，原价为空时斜杠后为空字符串。 */
  private static String encodePrice(Map<String, Object> row) {
    BigDecimal originalPrice = RowValues.decimal(row, "original_price");
    String original = originalPrice == null ? "" : originalPrice.toPlainString();
    return RowValues.decimal(row, "price").toPlainString() + PRICE_SEPARATOR + original;
  }

  private long jitteredTtlMillis(String key) {
    Duration baseTtl = key.startsWith(PRICE_KEY_PREFIX) ? priceTtl : stockTtl;
    return ttl.jitter(baseTtl).toMillis();
  }

  /** 组装 SKU 报价，按价格升序、同价按 ID 升序。缺价格或缺库存的 SKU 跳过（查询期间可能刚好下架）。 */
  private static List<SkuVO> toOffers(
      ProductProfile profile, Map<String, String> values, Map<Long, Integer> poolAvailable) {
    List<SkuVO> offers = new ArrayList<>();
    for (ProductProfile.Variant sku : profile.skus) {
      String encodedPrice = values.get(priceKey(sku.id));
      Integer stock = poolAvailable.get(sku.id);
      if (stock == null && values.containsKey(stockKey(sku.id))) {
        stock = Integer.valueOf(values.get(stockKey(sku.id)));
      }
      if (encodedPrice == null || stock == null) {
        continue;
      }
      String[] priceParts = encodedPrice.split(PRICE_SEPARATOR, -1);
      BigDecimal price = new BigDecimal(priceParts[0]);
      BigDecimal originalPrice = priceParts[1].isEmpty() ? null : new BigDecimal(priceParts[1]);
      offers.add(
          new SkuVO(sku.id, sku.skuCode, sku.spec, price, originalPrice, stock, sku.attributes));
    }
    offers.sort(Comparator.comparing(SkuVO::getPrice).thenComparingLong(SkuVO::getId));
    return offers;
  }

  /** 取出缓存命中且格式正确的值。格式损坏的 key 用 CAS 删除（只删这个坏值），只影响它自己，下次会回源重建。 */
  private Map<String, String> wellFormedValues(Map<String, RedisCatalogStore.Lookup> reads) {
    Map<String, String> result = new HashMap<>();
    reads.forEach(
        (key, read) -> {
          String value = read.getValue();
          if (value == null) {
            return;
          }
          if (isWellFormed(key, value)) {
            result.put(key, value);
          } else {
            store.discard(key, value);
          }
        });
    return result;
  }

  /** 价格值必须是"数字/数字"或"数字/"；库存值必须是非负整数。 */
  private static boolean isWellFormed(String key, String value) {
    try {
      if (key.startsWith(PRICE_KEY_PREFIX)) {
        String[] parts = value.split(PRICE_SEPARATOR, -1);
        if (parts.length != 2) {
          return false;
        }
        new BigDecimal(parts[0]);
        if (!parts[1].isEmpty()) {
          new BigDecimal(parts[1]);
        }
        return true;
      }
      return Integer.parseInt(value) >= 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static String priceKey(long skuId) {
    return PRICE_KEY_PREFIX + skuId;
  }

  private static String stockKey(long skuId) {
    return STOCK_KEY_PREFIX + skuId;
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "规格可售库存暂不可核实，请稍后重试");
  }
}
