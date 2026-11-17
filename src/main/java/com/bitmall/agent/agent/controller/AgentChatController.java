package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.dto.ChatRequest;
import com.bitmall.agent.agent.service.chat.AgentChatService;
import com.bitmall.agent.agent.vo.AgentReplyVO;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.Disposables;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;

/** 聊天 HTTP 入口。 普通聊天一次返回完整 JSON，流式聊天通过 SSE 分段发送；Controller 负责网络请求和连接，具体流程交给 AgentChatService。 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {
    /** 依赖组件：一轮聊天的总调度服务。 */
    private final AgentChatService chats;

    /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
    public AgentChatController(AgentChatService chats) {
        this.chats = chats;
    }

    /** 接收普通聊天请求，校验消息后交给聊天服务，一次返回完整回复。 */
    @PostMapping("/chat")
    public AgentReplyVO chat(@Valid @RequestBody ChatRequest request) {
        return chats.chat(
                request.resolvedUserId(), request.getConversationId(), request.getMessage());
    }

    /** 创建 SSE 长连接，把聊天服务产生的事件逐条推送给页面；连接结束、超时或异常时取消订阅。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(900_000L);
        reactor.core.Disposable.Swap subscription = Disposables.swap();
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(error -> subscription.dispose());
        subscription.update(
                chats.chatStream(
                                request.resolvedUserId(),
                                request.getConversationId(),
                                request.getMessage())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                event -> send(emitter, event),
                                emitter::completeWithError,
                                emitter::complete));
        return emitter;
    }

    /** 把项目事件转换为带事件名的 SSE 消息；网络发送失败时关闭当前连接。 */
    private void send(SseEmitter emitter, com.bitmall.agent.agent.vo.StreamEventVO event) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(event.getType())
                            .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
    }
}
