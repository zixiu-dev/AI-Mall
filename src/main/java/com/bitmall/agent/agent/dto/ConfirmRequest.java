package com.bitmall.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/** 确认接口的请求参数。 approved 必须明确为 true 或 false，不能省略；这里表达用户决定，不携带订单金额或商品参数。 */
public final class ConfirmRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 用户是否同意执行：true 为确认，false 为取消。 */
    @NotNull private final Boolean approved;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public ConfirmRequest(
            @JsonProperty("userId") Long userId, @JsonProperty("approved") Boolean approved) {
        this.userId = userId;
        this.approved = approved;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public Long getUserId() {
        return userId;
    }

    /** 获取用户是否同意执行：true 为确认，false 为取消。 */
    public Boolean getApproved() {
        return approved;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ConfirmRequest that = (ConfirmRequest) other;
        return Objects.equals(userId, that.userId) && Objects.equals(approved, that.approved);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(userId, approved);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "ConfirmRequest[" + "userId=" + userId + ", approved=" + approved + "]";
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
