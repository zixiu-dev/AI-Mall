package com.bitmall.agent.agent.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 用户明确要求保存的长期购物偏好，例如鞋码或选购习惯。 它跨会话使用，区别于 ChatMessageDO 这样的单条聊天记录。 */
@Getter
@EqualsAndHashCode
@ToString
public final class UserMemoryDO {
    /** 偏好名称，例如 shoe_size。 */
    private final String key;

    /** 用户明确要求保存的购物偏好内容。 */
    private final String value;

    @JsonCreator
    public UserMemoryDO(@JsonProperty("key") String key, @JsonProperty("value") String value) {
        this.key = key;
        this.value = value;
    }
}
