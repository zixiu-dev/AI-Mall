package com.bitmall.agent.order.inventory;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.order.config.InventoryReservationProperties;
import com.bitmall.agent.order.dao.mapper.OrderInventoryMapper;
import java.util.Map;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 库存池只初始化一次。初始化标记持久化到 DB，Redis 丢失时禁止直接用数据库余额重建。 */
@Service
@DependsOnDatabaseInitialization
public class InventoryPoolService {
  private final OrderInventoryMapper mapper;
  private final InventoryRedisStore redis;
  private final InventoryReservationProperties properties;

  /** 仅缓存已提交的 true，逐出后重新查库；不缓存库存数量。 */
  private final com.github.benmanes.caffeine.cache.Cache<Long, Boolean> ready =
      com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).build();

  public InventoryPoolService(
      OrderInventoryMapper mapper,
      InventoryRedisStore redis,
      InventoryReservationProperties properties) {
    this.mapper = mapper;
    this.redis = redis;
    this.properties = properties;
  }

  /** 防止开关改变后数据库直扣绕过原来尚未落库的 Redis 预占。 */
  @jakarta.annotation.PostConstruct
  public void checkDisabledPath() {
    if (!properties.isEnabled() && mapper.initializedCount() > 0)
      throw new IllegalStateException("已有 Redis 交易库存池，不能直接关闭预扣并回退数据库；请先停流、排空和核对库存");
  }

  /** 池键缺失直接失败；不能把尚含未落库预占的库存重新覆盖成数据库余额。 */
  public void checkReady(long sku) {
    if (!redis.poolExists(sku))
      throw new IllegalStateException("已初始化的 Redis 库存池丢失，请停止接单并核对，不能盲目重建");
  }

  /** 普通请求只读初始化标记，只有首次才需要竞争 SKU 行锁。 */
  public boolean initialized(long sku) {
    if (Boolean.TRUE.equals(ready.getIfPresent(sku))) return true;
    boolean value =
        RowValues.bool(
            RowValues.required(mapper.sku(Map.of("sku", sku))), "redis_inventory_initialized");
    if (value) ready.put(sku, true);
    return value;
  }

  /** 仅在调用初始化事务代理已经成功返回之后缓存，避免暴露未提交基线。 */
  public void rememberInitialized(long sku) {
    ready.put(sku, true);
  }

  /** 独立事务在父建单事务扣数据库库存之前结束，其他入口不能使用尚未提交的基线。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void initialize(long sku) {
    var row = RowValues.required(mapper.lockSku(Map.of("sku", sku)));
    if (!RowValues.bool(row, "redis_inventory_initialized")) {
      redis.initialize(sku, RowValues.intValue(row, "stock"));
      mapper.initialized(Map.of("sku", sku));
    } else if (!redis.poolExists(sku)) throw new IllegalStateException("Redis 库存池丢失，请停流核对");
  }
}
