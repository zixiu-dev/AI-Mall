package com.bitmall.agent.product.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Objects;

/** 读取 app.hotkey 配置。 说明京东 HotKey 是否启用、属于哪个应用、通过哪个 etcd 发现服务，以及访问统计的上报频率。 */
@ConfigurationProperties("app.hotkey")
public final class HotKeyProperties {
    /** 是否启用此功能。 */
    private final boolean enabled;

    /** 京东 HotKey 使用的应用名称，需要与热点规则一致。 */
    private final String appName;

    /** etcd 服务地址，用于发现 HotKey Worker 和规则。 */
    private final String etcdServer;

    /** 访问统计上报周期，单位毫秒。 */
    private final long pushPeriodMillis;

    /** HotKey 客户端最多跟踪的键数量。 */
    private final int maximumKeys;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    @ConstructorBinding
    public HotKeyProperties(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("appName") String appName,
            @JsonProperty("etcdServer") String etcdServer,
            @JsonProperty("pushPeriodMillis") long pushPeriodMillis,
            @JsonProperty("maximumKeys") int maximumKeys) {
        appName = appName == null || appName.isBlank() ? "wowo-mall" : appName;
        etcdServer = etcdServer == null ? "http://127.0.0.1:2379" : etcdServer;
        pushPeriodMillis = pushPeriodMillis > 0 ? pushPeriodMillis : 500;
        maximumKeys = maximumKeys > 0 ? maximumKeys : 10000;
        this.enabled = enabled;
        this.appName = appName;
        this.etcdServer = etcdServer;
        this.pushPeriodMillis = pushPeriodMillis;
        this.maximumKeys = maximumKeys;
    }

    /** 获取是否启用此功能。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 获取京东 HotKey 使用的应用名称，需要与热点规则一致。 */
    public String getAppName() {
        return appName;
    }

    /** 获取etcd 服务地址，用于发现 HotKey Worker 和规则。 */
    public String getEtcdServer() {
        return etcdServer;
    }

    /** 获取访问统计上报周期，单位毫秒。 */
    public long getPushPeriodMillis() {
        return pushPeriodMillis;
    }

    /** 获取HotKey 客户端最多跟踪的键数量。 */
    public int getMaximumKeys() {
        return maximumKeys;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        HotKeyProperties that = (HotKeyProperties) other;
        return Objects.equals(enabled, that.enabled)
                && Objects.equals(appName, that.appName)
                && Objects.equals(etcdServer, that.etcdServer)
                && Objects.equals(pushPeriodMillis, that.pushPeriodMillis)
                && Objects.equals(maximumKeys, that.maximumKeys);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(enabled, appName, etcdServer, pushPeriodMillis, maximumKeys);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "HotKeyProperties["
                + "enabled="
                + enabled
                + ", appName="
                + appName
                + ", etcdServer="
                + etcdServer
                + ", pushPeriodMillis="
                + pushPeriodMillis
                + ", maximumKeys="
                + maximumKeys
                + "]";
    }
}
