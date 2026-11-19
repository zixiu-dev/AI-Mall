package com.bitmall.agent.product.service;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.inventory.InventoryRedisStore;
import com.bitmall.agent.product.cache.*;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import com.bitmall.agent.product.vo.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** 将商品资料和公开报价拼成一次详情响应；交易预扣量始终只读，不能拿展示值确认成交。 */
@Service
public class SkuDisplayService {
  private final ProductRepository products;
  private final CatalogCache cache;
  private final RedisCatalogStore store;
  private final InventoryRedisStore pools;
  private final InventoryReservationProperties inventory;
  private final CacheTtlPolicy ttl;
  private final CatalogCacheProperties config;
  private final CacheMetrics metrics;
  private final Duration priceTtl, stockTtl;

  public SkuDisplayService(
      ProductRepository products,
      CatalogCache cache,
      RedisCatalogStore store,
      InventoryRedisStore pools,
      InventoryReservationProperties inventory,
      CacheTtlPolicy ttl,
      CatalogCacheProperties config,
      CacheMetrics metrics,
      @Value("${app.catalog-cache.price-ttl:60s}") Duration priceTtl,
      @Value("${app.catalog-cache.stock-ttl:3s}") Duration stockTtl) {
    this.products = products;
    this.cache = cache;
    this.store = store;
    this.pools = pools;
    this.inventory = inventory;
    this.ttl = ttl;
    this.config = config;
    this.metrics = metrics;
    this.priceTtl = priceTtl;
    this.stockTtl = stockTtl;
  }

  public ProductDetailVO detail(ProductProfile profile) {
    List<Long> ids = profile.skus.stream().map(v -> v.id).toList();
    if (ids.isEmpty()) return profile.withOffers(List.of());
    boolean pooled = inventory.isEnabled();
    Map<Long, Integer> available;
    try {
      available = pooled ? pools.availableForDisplay(ids) : Map.of();
    } catch (RuntimeException ex) {
      throw unavailable();
    }
    var names = new ArrayList<String>();
    for (long id : ids) {
      names.add(price(id));
      if (!pooled) names.add(stock(id));
    }
    boolean shared =
        config.isEnabled() && !TransactionSynchronizationManager.isActualTransactionActive();
    var first = shared ? store.lookupMany(names) : Map.<String, RedisCatalogStore.Lookup>of();
    var values = decoded(first);
    if (values.size() != names.size() || (pooled && available.size() != ids.size())) {
      values =
          cache.loadOffers(
              profile.id,
              shared && store.available(),
              owner -> {
                var reads =
                    shared ? store.lookupMany(names) : Map.<String, RedisCatalogStore.Lookup>of();
                var found = decoded(reads);
                var updates = new HashMap<String, String>();
                var missing =
                    ids.stream()
                        .filter(
                            id ->
                                !found.containsKey(price(id))
                                    || (!available.containsKey(id)
                                        && !found.containsKey(stock(id))))
                        .toList();
                if (!missing.isEmpty()) metrics.increment("sku_batch_db_load");
                for (var row : products.displayRows(missing)) {
                  long id = RowValues.longValue(row, "id");
                  if (!found.containsKey(price(id))) {
                    var original = RowValues.decimal(row, "original_price");
                    updates.put(
                        price(id),
                        RowValues.decimal(row, "price").toPlainString()
                            + "/"
                            + (original == null ? "" : original.toPlainString()));
                  }
                  if (!available.containsKey(id) && !found.containsKey(stock(id))) {
                    // 只有从未初始化的池才允许读 SQL 库存；丢失的池不能被误认为仍有货。
                    if (pooled && RowValues.bool(row, "redis_inventory_initialized"))
                      throw unavailable();
                    String count = String.valueOf(RowValues.intValue(row, "stock"));
                    if (pooled) found.put(stock(id), count);
                    else updates.put(stock(id), count);
                  }
                }
                if (shared)
                  store.fillOffers(
                      "offers:" + profile.id,
                      owner,
                      reads,
                      updates,
                      key ->
                          ttl.jitter(key.startsWith("sku:price:") ? priceTtl : stockTtl)
                              .toMillis());
                found.putAll(updates);
                return found;
              });
    }
    var offers = new ArrayList<SkuVO>();
    for (var v : profile.skus) {
      String amount = values.get(price(v.id));
      Integer quantity = available.get(v.id);
      if (quantity == null && values.containsKey(stock(v.id)))
        quantity = Integer.valueOf(values.get(stock(v.id)));
      if (amount == null || quantity == null) continue; // 查询期间规格可能下架。
      String[] parts = amount.split("/", -1);
      offers.add(
          new SkuVO(
              v.id,
              v.skuCode,
              v.spec,
              new BigDecimal(parts[0]),
              parts[1].isEmpty() ? null : new BigDecimal(parts[1]),
              quantity,
              v.attributes));
    }
    offers.sort(Comparator.comparing(SkuVO::getPrice).thenComparingLong(SkuVO::getId));
    return profile.withOffers(offers);
  }

  /** 损坏的单个展示键只影响自身。锁后重新读取取得新的回填凭据。 */
  private Map<String, String> decoded(Map<String, RedisCatalogStore.Lookup> reads) {
    var result = new HashMap<String, String>();
    reads.forEach(
        (key, read) -> {
          String value = read.getValue();
          if (value == null) return;
          try {
            if (key.startsWith("sku:price:")) {
              var parts = value.split("/", -1);
              if (parts.length != 2) throw new NumberFormatException();
              new BigDecimal(parts[0]);
              if (!parts[1].isEmpty()) new BigDecimal(parts[1]);
            } else if (Integer.parseInt(value) < 0) throw new NumberFormatException();
            result.put(key, value);
          } catch (NumberFormatException ex) {
            store.discard(key, value);
          }
        });
    return result;
  }

  private static String price(long id) {
    return "sku:price:" + id;
  }

  private static String stock(long id) {
    return "sku:availability:" + id;
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "规格可售库存暂不可核实，请稍后重试");
  }
}
