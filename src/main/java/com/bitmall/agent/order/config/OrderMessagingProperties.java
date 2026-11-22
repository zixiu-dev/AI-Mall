package com.bitmall.agent.order.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 订单消息配置。管理开关、Topic、每批发送数量、发送租约和失败重试间隔；连接参数仍使用标准 rocketmq 配置。 */
@Getter
@ConfigurationProperties("app.order-messaging")
public final class OrderMessagingProperties {
    /** 是否启动 RocketMQ 连接、后台发送和消息消费；关闭时仍保存本地待发送消息。 */
    private final boolean enabled;

    /** 订单事件使用的主题，环境不同应使用不同的主题。 */
    private final String topic;

    /** 一轮最多尝试发送多少条消息，防止积压时长时间占用调度线程。 */
    private final int batchSize;

    /** 抢到消息后的占用秒数，进程异常退出后其他实例可以重新发送。 */
    private final int leaseSeconds;

    /** 首次失败后的等待秒数，后续按失败次数增加，最多等待五分钟。 */
    private final int retryDelaySeconds;

    /** Spring 把配置传入普通构造方法；缺省值在这里补齐。 */
    public OrderMessagingProperties(
            boolean enabled, String topic, int batchSize, int leaseSeconds, int retryDelaySeconds) {
        this.enabled = enabled;
        this.topic = topic == null || topic.isBlank() ? "wowo_mall_order_events_dev" : topic;
        this.batchSize = batchSize > 0 ? Math.min(batchSize, 100) : 20;
        this.leaseSeconds = leaseSeconds > 0 ? leaseSeconds : 60;
        this.retryDelaySeconds = retryDelaySeconds > 0 ? retryDelaySeconds : 10;
    }
}
