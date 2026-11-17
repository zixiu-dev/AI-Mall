package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.service.conversation.ConversationService;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 读取聊天历史的 HTTP 入口。 通过 ConversationService 检查会话属于哪个用户，再返回最近消息，避免绕过服务层直接查表。 */
@RestController
@RequestMapping("/api/agent")
public class ConversationController {
    /** 依赖组件：会话身份和消息访问的业务入口。 */
    private final ConversationService conversations;

    /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    /** 通过会话服务检查归属后返回最近聊天记录；演示用户编号默认是 10001。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> messages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "10001") long userId) {
        return conversations.messages(userId, conversationId);
    }
}
