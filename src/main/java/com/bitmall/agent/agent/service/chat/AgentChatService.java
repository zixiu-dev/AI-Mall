package com.bitmall.agent.agent.service.chat;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.model.TextChunk;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.conversation.ConversationSummaryService;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.agent.service.runtime.SpringAiResponder;
import com.bitmall.agent.agent.service.runtime.demo.DemoAgentResponder;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import com.bitmall.agent.agent.vo.StreamEventVO;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一轮聊天的总调度服务，页面普通聊天和 SSE 流式聊天共用。
 *
 * <p>流程：创建或校验会话 -> 获取会话执行权（同一会话同时只跑一轮）-> 如果还有待确认操作就只提醒用户 -> 保存用户消息 -> 由真实模型或本地演示生成回复 -> 保存回复和确认卡关联。
 *
 * <p>本类只负责"提出"操作；用户点击确认后的执行由 AgentActionService 负责。
 */
@Service
@RequiredArgsConstructor
public class AgentChatService {
    private static final Logger log =
            LoggerFactory.getLogger(AgentChatService.class);

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /** 消息元数据中关联确认卡的键。 */
    private static final String PENDING_ACTION_ID_KEY = "pendingActionId";

    private static final String EMPTY_REPLY = "我暂时没有生成有效回答，请换个说法再试。";

    /** 创建或校验会话归属，保存本轮用户与助手消息，读取最近历史。 */
    private final ConversationService conversations;

    /** 查询会话里是否有待用户确认的操作；有则本轮不交给模型，只提醒用户先处理确认卡。 */
    private final PendingActionService pendingActions;

    /** 真实模型模式：运行模型与工具的循环。 */
    private final SpringAiResponder springAiResponder;

    /** 演示模式：本地关键词规则助手，仅在明确关闭 AI 时使用。 */
    private final DemoAgentResponder demoResponder;

    /** 单进程内的会话并发门禁，保证同一会话同一时刻只处理一轮聊天。 */
    private final AgentRunGate runGate;

    /** 本轮结束后历史过长时，把较早轮次压缩为摘要。 */
    private final ConversationSummaryService summaryService;

    /** 读取是否启用真实模型，以及送给模型的最近消息条数。 */
    private final AgentProperties properties;

    /** 把回复和真实确认卡组装成前端返回对象。 */
    private final AgentReplyService replies;

    /** 普通聊天入口：获取会话执行权后处理一轮聊天，请求结束时自动释放。 */
    public AgentReplyVO chat(long userId, String requestedConversationId, String message) {
        String conversationId = conversations.ensure(requestedConversationId, userId, message);
        try (AgentRunGate.Lease ignored = runGate.acquire(conversationId)) {
            return chatLocked(userId, conversationId, message);
        }
    }

    /** 已持有执行权：先拦截待确认操作，再保存输入、生成回复并保存结果。 */
    private AgentReplyVO chatLocked(long userId, String conversationId, String message) {
        // 1. 上一步的确认卡还没处理时，不把新的业务请求交给模型，只提醒用户先确认或取消。
        PendingActionDO existing = pendingActions.latest(conversationId);
        if (existing != null) {
            return pendingReminder(conversationId, existing);
        }

        // 2. 先保存用户输入并整理历史，保证模型看到的是已经入库的上下文。
        ChatRound round = prepareRound(conversationId, message);

        // 3. 生成回复。演示模式只由配置决定；模型故障时不会自动切到演示，避免意外走演示交易流程。
        String content;
        if (properties.isAiEnabled()) {
            content = respondWithModel(userId, conversationId, message, round);
        } else {
            content = respondWithDemo(userId, conversationId, message, round);
        }
        return completeRound(conversationId, content);
    }

    /**
     * SSE 流式聊天入口，在工作线程中同步执行，事件通过 events 回调逐条发给 Controller。
     *
     * <p>事件顺序：start -> （待确认提醒的 delta + done）或（模型/演示产生的 delta、reset、工具进度 -> done 或 error）。
     *
     * <p>取消（CancellationException）始终向外抛出，交给 Controller 处理连接；其他失败保存为一条错误消息并发送 error 事件。
     */
    public void chatStream(
            long userId,
            String requestedConversationId,
            String message,
            AgentExecution execution,
            Consumer<StreamEventVO> events) {
        execution.bind();
        execution.check();
        String conversationId = conversations.ensure(requestedConversationId, userId, message);
        try (AgentRunGate.Lease ignored = runGate.acquire(conversationId)) {
            execution.check();
            events.accept(StreamEventVO.start(conversationId));

            PendingActionDO existing = pendingActions.latest(conversationId);
            if (existing != null) {
                AgentReplyVO reminder = pendingReminder(conversationId, existing);
                events.accept(StreamEventVO.delta(conversationId, reminder.getContent()));
                events.accept(StreamEventVO.done(reminder));
                return;
            }

            ChatRound round = prepareRound(conversationId, message);
            try {
                String content;
                if (properties.isAiEnabled()) {
                    content = streamWithModel(userId, conversationId, message, round, execution, events);
                } else {
                    content = streamWithDemo(userId, conversationId, message, round, execution, events);
                }
                if (content == null || content.isBlank()) {
                    content = EMPTY_REPLY;
                }
                // 客户端已断开时不写入完整成功回复；已经生成的确认操作仍保存在数据库中。
                execution.checkConnected();
                events.accept(StreamEventVO.done(completeRound(conversationId, content)));
            } catch (CancellationException error) {
                throw error;
            } catch (Exception error) {
                String failure = "这次请求没有处理成功：" + rootMessage(error);
                conversations.addMessage(
                        conversationId, ROLE_ASSISTANT, failure, Map.of("streamError", true));
                events.accept(StreamEventVO.error(conversationId, failure));
            }
        }
    }

    /** 普通聊天调用真实模型；任何异常都记录日志并改用兜底回复，不向调用方抛出。 */
    private String respondWithModel(
            long userId, String conversationId, String message, ChatRound round) {
        try {
            return springAiResponder.respond(
                    userId,
                    conversationId,
                    round.getRequestId(),
                    message,
                    round.getRecent());
        } catch (Exception error) {
            log.warn("Spring AI run failed [{}]: {}", conversationId, rootMessage(error));
            return fallbackResponse(conversationId);
        }
    }

    /** 调用本地演示助手生成完整回复。 */
    private String respondWithDemo(
            long userId, String conversationId, String message, ChatRound round) {
        return demoResponder.respond(
                userId,
                conversationId,
                round.getRequestId(),
                message,
                round.getRecent());
    }

    /**
     * 流式调用真实模型，文本和工具进度实时转发。
     *
     * <p>取消直接抛出；其他异常记录日志，然后先发 reset 清空页面上已显示的半截回答，再发送兜底回复。
     */
    private String streamWithModel(
            long userId,
            String conversationId,
            String message,
            ChatRound round,
            AgentExecution execution,
            Consumer<StreamEventVO> events) {
        try {
            return springAiResponder.respondStream(
                    userId,
                    conversationId,
                    round.getRequestId(),
                    message,
                    round.getRecent(),
                    execution,
                    chunk -> sendChunk(conversationId, chunk, events));
        } catch (CancellationException error) {
            throw error;
        } catch (Exception error) {
            log.warn(
                    "Spring AI stream failed [{}]: {}",
                    conversationId,
                    rootMessage(error));
            String fallback = fallbackResponse(conversationId);
            events.accept(StreamEventVO.reset(conversationId));
            events.accept(StreamEventVO.delta(conversationId, fallback));
            return fallback;
        }
    }

    /** 演示助手不支持增量输出：生成完整回复后，确认请求未被取消，再一次性作为 delta 发送。 */
    private String streamWithDemo(
            long userId,
            String conversationId,
            String message,
            ChatRound round,
            AgentExecution execution,
            Consumer<StreamEventVO> events) {
        String content = respondWithDemo(userId, conversationId, message, round);
        execution.check();
        events.accept(StreamEventVO.delta(conversationId, content));
        return content;
    }

    /** 把模型循环的内部片段转成 SSE 事件；reset 表示模型转去调用工具或改写回答，页面需清空临时正文。 */
    private void sendChunk(String conversationId, TextChunk chunk, Consumer<StreamEventVO> events) {
        boolean toolProgress = chunk.getType().startsWith("tool_");
        if (toolProgress) {
            events.accept(
                    new StreamEventVO(chunk.getType(), conversationId, chunk.getContent(), null));
        } else if ("reset".equals(chunk.getType())) {
            events.accept(StreamEventVO.reset(conversationId));
        } else {
            events.accept(StreamEventVO.delta(conversationId, chunk.getContent()));
        }
    }

    /** 保存本轮用户消息、按需整理旧摘要，再读取最近历史并生成本轮请求编号。 */
    private ChatRound prepareRound(String conversationId, String message) {
        conversations.addMessage(conversationId, ROLE_USER, message, Map.of());
        summaryService.compactIfNeeded(conversationId);
        return new ChatRound(
                UUID.randomUUID().toString(),
                conversations.recentMessages(conversationId, properties.getRecentMessageLimit()));
    }

    /**
     * 保存最终回复。
     *
     * <p>如果本轮产生了待确认操作，用后端生成的确认文案替换模型的措辞：是否真的有操作以数据库为准，不能相信模型自称"已经下单"。
     */
    private AgentReplyVO completeRound(String conversationId, String content) {
        PendingActionDO pending = pendingActions.latest(conversationId);
        String finalContent = content;
        Map<String, Object> metadata = Map.of();
        if (pending != null) {
            finalContent = replies.confirmationMessage(pending);
            metadata = Map.of(PENDING_ACTION_ID_KEY, pending.getId());
        }
        long messageId =
                conversations.addMessage(conversationId, ROLE_ASSISTANT, finalContent, metadata);
        summaryService.compactIfNeeded(conversationId);
        return replies.create(conversationId, messageId, finalContent, pending);
    }

    /** 已有操作待确认时只保存一条提醒，不把新的业务请求交给模型执行。 */
    private AgentReplyVO pendingReminder(String conversationId, PendingActionDO pending) {
        String content = "上一步操作还在等待确认，请先确认或取消：" + pending.getSummary();
        long messageId =
                conversations.addMessage(
                        conversationId,
                        ROLE_ASSISTANT,
                        content,
                        Map.of(PENDING_ACTION_ID_KEY, pending.getId()));
        return replies.create(conversationId, messageId, content, pending);
    }

    /** 模型故障时的兜底回复：如果确认操作已经准备好就引导用户核对，否则提示稍后再试，不自动转入演示交易。 */
    private String fallbackResponse(String conversationId) {
        PendingActionDO pending = pendingActions.latest(conversationId);
        if (pending != null) {
            return "AI 服务暂时不可用，但操作已经准备完成，请核对下方确认卡：" + pending.getSummary();
        }
        return "AI 服务暂时不可用，本次未自动转入演示流程。请稍后重试，或通过商城页面继续操作。";
    }

    /** 沿异常原因链找到最内层错误信息，供日志和失败提示使用。 */
    private static String rootMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root.getMessage() == null) {
            return "未知错误";
        }
        return root.getMessage();
    }

    /** 一轮聊天内部使用的数据：本轮请求编号（用于追踪和生成幂等键）和最近消息，不写入数据库。 */
    private static final class ChatRound {
        private final String requestId;

        private final List<ChatMessageDO> recent;

        ChatRound(String requestId, List<ChatMessageDO> recent) {
            this.requestId = requestId;
            this.recent = recent;
        }

        String getRequestId() {
            return requestId;
        }

        List<ChatMessageDO> getRecent() {
            return recent;
        }
    }
}
