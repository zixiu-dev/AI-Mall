package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理每个 SKU 的 Redis 库存池初始化。
 *
 * <p>每个 SKU 的库存池只用数据库库存初始化一次，初始化后在数据库 SKU 行上打标记（redis_inventory_initialized）。 此后 Redis 才是可售量的准确来源：
 * 数据库余额不包含还在 Redis 里、尚未落库的预占。所以一旦已初始化的池在 Redis 中丢失， 绝不能再用数据库余额重建，只能停止接单、人工核对。
 */
@Service
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class InventoryPoolService {
  private static final String INITIALIZED_COLUMN = "redis_inventory_initialized";

  /** 读取和锁定 SKU 行上的初始化标记与库存，写入初始化标记。 */
  private final OrderInventoryMapper mapper;

  /** 检查 Redis 库存池是否存在，并用数据库库存初始化它。 */
  private final InventoryRedisStore redis;

  /** 读取是否启用 Redis 库存预占，启动时检查开关是否被不安全地关闭。 */
  private final InventoryReservationProperties properties;

  /** 本地缓存"已初始化"的 SKU，避免每次请求都查库。只缓存 true（初始化不可逆），不缓存库存数量。 */
  private final Cache<Long, Boolean> ready = Caffeine.newBuilder().maximumSize(10000).build();

  /**
   * 启动检查：已经有 SKU 初始化过 Redis 库存池时，不允许关闭预占开关。
   *
   * <p>否则下单会直接扣数据库库存，绕过 Redis 里还没落库的预占，导致超卖。
   */
  @PostConstruct
  public void checkDisabledPath() {
    if (!properties.isEnabled() && mapper.initializedCount() > 0) {
      throw new IllegalStateException("已有 Redis 交易库存池，不能直接关闭预扣并回退数据库；请先停流、排空和核对库存");
    }
  }

  /** 已初始化的 SKU 预占前调用：库存池必须还在，丢失时直接失败，不能用数据库余额覆盖。 */
  public void checkReady(long sku) {
    if (!redis.poolExists(sku)) {
      throw new IllegalStateException("已初始化的 Redis 库存池丢失，请停止接单并核对，不能盲目重建");
    }
  }

  /** 查询 SKU 是否已初始化库存池。只读标记不加锁，只有首次初始化才需要竞争 SKU 行锁。 */
  public boolean initialized(long sku) {
    if (Boolean.TRUE.equals(ready.getIfPresent(sku))) {
      return true;
    }
    Map<String, Object> skuRow = RowValues.required(mapper.sku(Map.of("sku", sku)));
    boolean initialized = RowValues.bool(skuRow, INITIALIZED_COLUMN);
    if (initialized) {
      ready.put(sku, true);
    }
    return initialized;
  }

  /** 由调用方在 {@link #initialize} 的事务成功提交（代理方法正常返回）后调用，避免把未提交的状态放进缓存。 */
  public void rememberInitialized(long sku) {
    ready.put(sku, true);
  }

  /**
   * 首次初始化 SKU 的库存池。
   *
   * <p>独立事务：必须在外层建单事务扣数据库库存之前提交，这样写入 Redis 的基线才是扣减前的、已提交的数据库库存， 其他请求也不会看到未提交的基线。 锁住 SKU
   * 行后再检查一次标记，并发初始化时只有一个请求真正写入。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void initialize(long sku) {
    Map<String, Object> skuRow = RowValues.required(mapper.lockSku(Map.of("sku", sku)));
    if (!RowValues.bool(skuRow, INITIALIZED_COLUMN)) {
      redis.initialize(sku, RowValues.intValue(skuRow, "stock"));
      mapper.initialized(Map.of("sku", sku));
    } else if (!redis.poolExists(sku)) {
      throw new IllegalStateException("Redis 库存池丢失，请停流核对");
    }
  }
}
