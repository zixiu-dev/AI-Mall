package com.bitmall.agent.product.config;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.util.Objects;

/** 读取 app.catalog-cache 配置。 集中定义缓存前缀、不同数据的过期时间、内存容量和数据库回源并发上限，构造方法会补齐安全默认值。 */
@Getter
@ConfigurationProperties("app.catalog-cache")
public final class CatalogCacheProperties {
    /** 是否启用此功能。 */
    private final boolean enabled;

    /** 缓存键前缀，用于隔离不同环境的缓存数据。 */
    private final String namespace;

    /** 商品详情在 Redis 中的有效时间。 */
    private final Duration detailTtl;

    /** 搜索结果在 Redis 中的有效时间。 */
    private final Duration searchTtl;

    /** 不存在商品的空结果缓存时间。 */
    private final Duration negativeTtl;

    /** 热点商品在本机内存中的有效时间。 */
    private final Duration localTtl;

    /** 本机缓存最多保存多少条数据。 */
    private final int localMaximumSize;

    /** Redis 故障后暂停访问的时间，防止持续重试。 */
    private final Duration redisCooldown;

    /** 最多允许多少个请求同时回源数据库。 */
    private final int maximumConcurrentLoads;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    @ConstructorBinding
    public CatalogCacheProperties(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("detailTtl") Duration detailTtl,
            @JsonProperty("searchTtl") Duration searchTtl,
            @JsonProperty("negativeTtl") Duration negativeTtl,
            @JsonProperty("localTtl") Duration localTtl,
            @JsonProperty("localMaximumSize") int localMaximumSize,
            @JsonProperty("redisCooldown") Duration redisCooldown,
            @JsonProperty("maximumConcurrentLoads") int maximumConcurrentLoads) {
        namespace = namespace == null || namespace.isBlank() ? "wowo:catalog:real:v2" : namespace;
        detailTtl = positive(detailTtl, Duration.ofMinutes(10));
        searchTtl = positive(searchTtl, Duration.ofSeconds(5));
        negativeTtl = positive(negativeTtl, Duration.ofSeconds(3));
        localTtl = positive(localTtl, Duration.ofSeconds(2));
        redisCooldown = positive(redisCooldown, Duration.ofSeconds(3));
        localMaximumSize = localMaximumSize > 0 ? localMaximumSize : 2000;
        maximumConcurrentLoads = maximumConcurrentLoads > 0 ? maximumConcurrentLoads : 16;
        this.enabled = enabled;
        this.namespace = namespace;
        this.detailTtl = detailTtl;
        this.searchTtl = searchTtl;
        this.negativeTtl = negativeTtl;
        this.localTtl = localTtl;
        this.localMaximumSize = localMaximumSize;
        this.redisCooldown = redisCooldown;
        this.maximumConcurrentLoads = maximumConcurrentLoads;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        CatalogCacheProperties that = (CatalogCacheProperties) other;
        return Objects.equals(enabled, that.enabled)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(detailTtl, that.detailTtl)
                && Objects.equals(searchTtl, that.searchTtl)
                && Objects.equals(negativeTtl, that.negativeTtl)
                && Objects.equals(localTtl, that.localTtl)
                && Objects.equals(localMaximumSize, that.localMaximumSize)
                && Objects.equals(redisCooldown, that.redisCooldown)
                && Objects.equals(maximumConcurrentLoads, that.maximumConcurrentLoads);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                namespace,
                detailTtl,
                searchTtl,
                negativeTtl,
                localTtl,
                localMaximumSize,
                redisCooldown,
                maximumConcurrentLoads);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CatalogCacheProperties["
                + "enabled="
                + enabled
                + ", namespace="
                + namespace
                + ", detailTtl="
                + detailTtl
                + ", searchTtl="
                + searchTtl
                + ", negativeTtl="
                + negativeTtl
                + ", localTtl="
                + localTtl
                + ", localMaximumSize="
                + localMaximumSize
                + ", redisCooldown="
                + redisCooldown
                + ", maximumConcurrentLoads="
                + maximumConcurrentLoads
                + "]";
    }

    /** 过期时间缺失、为零或为负时改用默认值。 */
    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
