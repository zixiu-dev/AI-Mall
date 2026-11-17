package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 用户明确要求保存的长期购物偏好，例如鞋码或选购习惯。 它跨会话使用，区别于 ChatMessageDO 这样的单条聊天记录。 */
public final class UserMemoryDO {
    /** 偏好名称，例如 shoe_size。 */
    private final String key;

    /** 用户明确要求保存的购物偏好内容。 */
    private final String value;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public UserMemoryDO(@JsonProperty("key") String key, @JsonProperty("value") String value) {
        this.key = key;
        this.value = value;
    }

    /** 获取偏好名称，例如 shoe_size。 */
    public String getKey() {
        return key;
    }

    /** 获取用户明确要求保存的购物偏好内容。 */
    public String getValue() {
        return value;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        UserMemoryDO that = (UserMemoryDO) other;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "UserMemoryDO[" + "key=" + key + ", value=" + value + "]";
    }
}
