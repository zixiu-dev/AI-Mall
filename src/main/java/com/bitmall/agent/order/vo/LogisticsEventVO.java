package com.bitmall.agent.order.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 订单物流轨迹中的一个时间节点，例如已揽收、运输中或派送中。 订单查询会把多个节点按时间排列后返回。 */
@Getter
@EqualsAndHashCode
@ToString
public final class LogisticsEventVO {
    private final LocalDateTime eventTime;

    /** 当前业务状态，由服务端维护。 */
    private final String status;

    /** 功能简述，帮助调用者判断用途。 */
    private final String description;

    @JsonCreator
    public LogisticsEventVO(
            @JsonProperty("eventTime") LocalDateTime eventTime,
            @JsonProperty("status") String status,
            @JsonProperty("description") String description) {
        this.eventTime = eventTime;
        this.status = status;
        this.description = description;
    }
}
