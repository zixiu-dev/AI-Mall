package com.bitmall.agent.agent.controller;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dto.ChatRequest;
import com.bitmall.agent.agent.service.chat.AgentChatService;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import com.bitmall.agent.agent.vo.StreamEventVO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;

/** 聊天 HTTP 入口。SSE 在有界工作线程执行同步循环，Controller 负责发送事件和连接取消。 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {
    /** 组织一轮聊天，检查会话和待确认操作，保存消息并选择模型执行方式。 */
    private final AgentChatService chats;

    /** 执行 SSE 聊天任务的独立线程池，最多 16 个工作线程和 32 个等待任务。 */
    @Qualifier("agentChatExecutor")
    private final ThreadPoolTaskExecutor executor;

    /** 安排模型和工具超时检查，向所属聊天线程发送中断信号。 */
    private final ScheduledExecutorService scheduler;

    /** 读取单轮聊天的总超时时间，创建本轮的 AgentExecution。 */
    private final AgentProperties properties;

    @PostMapping("/chat")
    public AgentReplyVO chat(@Valid @RequestBody ChatRequest request) {
        return chats.chat(
                request.resolvedUserId(), request.getConversationId(), request.getMessage());
    }

    /** 排队也计入请求预算；断连和容器超时中断所属工作线程，门禁由其 finally 释放。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter((properties.getRequestTimeoutSeconds() + 5L) * 1000L);
        AgentExecution execution =
                new AgentExecution(scheduler, properties.getRequestTimeoutSeconds());
        emitter.onCompletion(execution::cancel);
        emitter.onTimeout(
                () -> {
                    execution.cancel();
                    emitter.complete();
                });
        emitter.onError(error -> execution.cancel());
        try {
            executor.execute(() -> run(request, emitter, execution));
        } catch (RuntimeException rejected) {
            execution.close();
            send(
                    emitter,
                    StreamEventVO.error(request.getConversationId(), "聊天请求较多，请稍后重试"),
                    execution);
            emitter.complete();
        }
        return emitter;
    }

    private void run(ChatRequest request, SseEmitter emitter, AgentExecution execution) {
        try (execution) {
            chats.chatStream(
                    request.resolvedUserId(),
                    request.getConversationId(),
                    request.getMessage(),
                    execution,
                    event -> send(emitter, event, execution));
        } catch (CancellationException stopped) {
            // 页面断开时结束本轮，已准备的确认卡与交易状态仍保存在数据库。
        } catch (Exception error) {
            emitter.completeWithError(error);
            return;
        }
        emitter.complete();
    }

    /** 发送失败同时取消执行，防止模型仍继续调用后面的工具。 */
    private void send(SseEmitter emitter, StreamEventVO event, AgentExecution execution) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(event.getType())
                            .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException error) {
            execution.cancel();
            throw new CancellationException("SSE 连接已经关闭");
        }
    }
}
