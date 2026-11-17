package com.bitmall.agent.product.cache;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 缓存状态的运维查看入口。 Actuator 调用它读取缓存、Redis、热点探测和计数信息，不参与商品交易。 */
@Component
@Endpoint(id = "catalogcache")
public class CatalogCacheEndpoint {
    /** 依赖组件：商品公共查询的多级缓存入口。 */
    private final CatalogCache cache;

    /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
    public CatalogCacheEndpoint(CatalogCache cache) {
        this.cache = cache;
    }

    /** 向 Actuator 返回只读运行状态，便于排查缓存是否命中或降级。 */
    @ReadOperation
    public Map<String, Object> status() {
        return cache.status();
    }
}
