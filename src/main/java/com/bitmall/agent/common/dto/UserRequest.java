package com.bitmall.agent.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 只需要确定操作用户的简单请求，例如手动领券和取消订单。 演示环境省略编号时使用 10001。 */
public final class UserRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public UserRequest(@JsonProperty("userId") Long userId) {
        this.userId = userId;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public Long getUserId() {
        return userId;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        UserRequest that = (UserRequest) other;
        return Objects.equals(userId, that.userId);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "UserRequest[" + "userId=" + userId + "]";
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }
}
