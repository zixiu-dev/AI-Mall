package com.bitmall.agent.agent.service.runtime;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;

import java.util.List;

/** 回复生成接口。 真实模型和本地演示回复都提供相同的普通回复入口，使聊天编排可以按配置选择实现。 */
public interface AgentResponder {

    /** 根据用户、会话、本轮编号和历史生成回复；实现可以是真实模型，也可以是本地演示规则。 */
    String respond(
            long userId,
            String conversationId,
            String requestId,
            String userMessage,
            List<ChatMessageDO> recentMessages);
}
