package com.bitmall.agent.agent.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 前端绘制确认卡需要的少量信息。 只暴露操作编号、工具名称和摘要，真正执行的参数保存在后端 PendingActionDO 中。 */
@Getter
@EqualsAndHashCode
@ToString
public final class PendingActionVO {
    private final String id;

    private final String toolName;

    private final String summary;

    @JsonCreator
    public PendingActionVO(
            @JsonProperty("id") String id,
            @JsonProperty("toolName") String toolName,
            @JsonProperty("summary") String summary) {
        this.id = id;
        this.toolName = toolName;
        this.summary = summary;
    }
}
