package com.bitmall.agent.product.cache;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 缓存状态的运维查看入口。 Actuator 调用它读取缓存、Redis、热点探测和计数信息，不参与商品交易。 */
@Component
@Endpoint(id = "catalogcache")
@RequiredArgsConstructor
public class CatalogCacheEndpoint {
    /** 读取商品缓存状态，通过 Actuator 端点输出。 */
    private final CatalogCache cache;

    /** 向 Actuator 返回只读运行状态，便于排查缓存是否命中或降级。 */
    @ReadOperation
    public Map<String, Object> status() {
        return cache.status();
    }
}
