package com.bitmall.agent.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 只需要确定操作用户的简单请求，例如手动领券和取消订单。 演示环境省略编号时使用 10001。 */
@Getter
@EqualsAndHashCode
@ToString
public final class UserRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    @JsonCreator
    public UserRequest(@JsonProperty("userId") Long userId) {
        this.userId = userId;
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
