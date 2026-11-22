package com.bitmall.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 确认接口的请求参数。 approved 必须明确为 true 或 false，不能省略；这里表达用户决定，不携带订单金额或商品参数。 */
@Getter
@EqualsAndHashCode
@ToString
public final class ConfirmRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 用户是否同意执行：true 为确认，false 为取消。 */
    @NotNull private final Boolean approved;

    @JsonCreator
    public ConfirmRequest(
            @JsonProperty("userId") Long userId, @JsonProperty("approved") Boolean approved) {
        this.userId = userId;
        this.approved = approved;
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
