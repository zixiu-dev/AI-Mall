package com.bitmall.agent.order.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/** 订单物流轨迹中的一个时间节点，例如已揽收、运输中或派送中。 订单查询会把多个节点按时间排列后返回。 */
public final class LogisticsEventVO {
    /** 物流事件发生时间。 */
    private final LocalDateTime eventTime;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 功能简述，帮助调用者判断用途。 */
    private final String description;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public LogisticsEventVO(
            @JsonProperty("eventTime") LocalDateTime eventTime,
            @JsonProperty("status") String status,
            @JsonProperty("description") String description) {
        this.eventTime = eventTime;
        this.status = status;
        this.description = description;
    }

    /** 获取物流事件发生时间。 */
    public LocalDateTime getEventTime() {
        return eventTime;
    }

    /** 获取当前业务状态，由服务端维护。 */
    public String getStatus() {
        return status;
    }

    /** 获取功能简述，帮助调用者判断用途。 */
    public String getDescription() {
        return description;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        LogisticsEventVO that = (LogisticsEventVO) other;
        return Objects.equals(eventTime, that.eventTime)
                && Objects.equals(status, that.status)
                && Objects.equals(description, that.description);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(eventTime, status, description);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "LogisticsEventVO["
                + "eventTime="
                + eventTime
                + ", status="
                + status
                + ", description="
                + description
                + "]";
    }
}
