package com.bitmall.agent.agent.service.chat;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.entity.PendingActionDO;
import com.bitmall.agent.agent.model.TextChunk;
import com.bitmall.agent.agent.service.action.PendingActionService;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.conversation.ConversationSummaryService;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.agent.service.runtime.SpringAiResponder;
import com.bitmall.agent.agent.service.runtime.demo.DemoAgentResponder;
import com.bitmall.agent.agent.vo.AgentReplyVO;
import com.bitmall.agent.agent.vo.StreamEventVO;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 一轮聊天的总调度服务。 创建或检查会话、阻止同会话并发、保存消息、选择模型或演示回复、整理最终结果；确认操作的执行另由 AgentActionService 负责。 */
@Service
public class AgentChatService {
    /** 日志记录器，用于排查异常和运行状态，不把内部信息直接发给用户。 */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AgentChatService.class);

    /** 依赖组件：会话身份和消息访问的业务入口。 */
    private final ConversationService conversations;

    /** 依赖组件：管理待确认操作。 */
    private final PendingActionService pendingActions;

    /** 依赖组件：真实模型的运行流程。 */
    private final SpringAiResponder springAiResponder;

    /** 依赖组件：无需模型密钥的本地演示实现。 */
    private final DemoAgentResponder demoResponder;

    /** 依赖组件：单进程的会话并发门禁。 */
    private final AgentRunGate runGate;

    /** 依赖组件：整理较早的聊天内容。 */
    private final ConversationSummaryService summaryService;

    /** 依赖组件：读取 application.yml 中 app.agent 配置。 */
    private final AgentProperties properties;

    /** 依赖组件：组装发给前端的回复和确认文案。 */
    private final AgentReplyService replies;

    /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
    public AgentChatService(
            ConversationService conversations,
            PendingActionService pendingActions,
            SpringAiResponder springAiResponder,
            DemoAgentResponder demoResponder,
            AgentRunGate runGate,
            ConversationSummaryService summaryService,
            AgentProperties properties,
            AgentReplyService replies) {
        this.conversations = conversations;
        this.pendingActions = pendingActions;
        this.springAiResponder = springAiResponder;
        this.demoResponder = demoResponder;
        this.runGate = runGate;
        this.summaryService = summaryService;
        this.properties = properties;
        this.replies = replies;
    }

    /** 普通聊天入口：检查会话并获取执行权，整个请求结束后自动释放。 */
    public AgentReplyVO chat(long userId, String requestedConversationId, String message) {
        String conversationId = conversations.ensure(requestedConversationId, userId, message);
        try (AgentRunGate.Lease ignored = runGate.acquire(conversationId)) {
            return chatLocked(userId, conversationId, message);
        }
    }

    /** 持有执行权后处理聊天：先拦截待确认操作，再准备历史、生成回复并保存结果。 */
    private AgentReplyVO chatLocked(long userId, String conversationId, String message) {
        // 1. 先处理上一步确认卡。存在待确认操作时，不把新的业务请求继续交给模型。
        PendingActionDO existing = pendingActions.latest(conversationId);
        if (existing != null) {
            return pendingReminder(conversationId, existing);
        }

        // 2. 先保存用户输入并整理历史，使模型看到的是本轮已经入库的上下文。
        ChatRound round = prepareRound(conversationId, message);
        String content;
        // 3. 开启 AI 才调用真实模型；本地演示是显式配置选择，模型故障不会自动切换交易模式。
        if (properties.isAiEnabled()) {
            try {
                content =
                        springAiResponder.respond(
                                userId,
                                conversationId,
                                round.getRequestId(),
                                message,
                                round.getRecent());
            } catch (Exception error) {
                log.warn("Spring AI run failed [{}]: {}", conversationId, rootMessage(error));
                content = fallbackResponse(conversationId);
            }
        } else {
            content =
                    demoResponder.respond(
                            userId,
                            conversationId,
                            round.getRequestId(),
                            message,
                            round.getRecent());
        }
        return completeRound(conversationId, content);
    }

    /** 流式聊天入口：订阅时再创建会话和获取执行权，结束或取消时释放。 */
    public Flux<StreamEventVO> chatStream(
            long userId, String requestedConversationId, String message) {
        // Flux 表示一串事件；defer 让下面的逻辑等到订阅时才执行，而不是定义事件流时就执行。
        return Flux.defer(
                () -> {
                    String conversationId =
                            conversations.ensure(requestedConversationId, userId, message);
                    // using 把会话执行权当作资源：正常结束、异常或页面断开，都会调用 Lease.close 释放。
                    return Flux.using(
                            () -> runGate.acquire(conversationId),
                            lease -> chatStreamLocked(userId, conversationId, message),
                            AgentRunGate.Lease::close);
                });
    }

    /** 把模型片段和工具进度转换成页面事件；结束时落库完整回复，异常时记录失败。 */
    private Flux<StreamEventVO> chatStreamLocked(
            long userId, String conversationId, String message) {
        // Flux 表示一串事件；defer 让下面的逻辑等到订阅时才执行，而不是定义事件流时就执行。
        return Flux.defer(
                () -> {
                    PendingActionDO existing = pendingActions.latest(conversationId);
                    if (existing != null) {
                        AgentReplyVO completed = pendingReminder(conversationId, existing);
                        return Flux.just(
                                StreamEventVO.start(conversationId),
                                StreamEventVO.delta(conversationId, completed.getContent()),
                                StreamEventVO.done(completed));
                    }

                    ChatRound round = prepareRound(conversationId, message);
                    Flux<TextChunk> source;
                    if (properties.isAiEnabled()) {
                        source =
                                springAiResponder
                                        .respondStream(
                                                userId,
                                                conversationId,
                                                round.getRequestId(),
                                                message,
                                                round.getRecent())
                                        .doOnError(
                                                error ->
                                                        log.warn(
                                                                "Spring AI stream failed [{}]: {}",
                                                                conversationId,
                                                                rootMessage(error)))
                                        .onErrorResume(
                                                error ->
                                                        Mono.fromCallable(
                                                                        () ->
                                                                                fallbackResponse(
                                                                                        conversationId))
                                                                .flatMapMany(
                                                                        fallback ->
                                                                                Flux.just(
                                                                                        TextChunk
                                                                                                .reset(),
                                                                                        TextChunk
                                                                                                .delta(
                                                                                                        fallback))));
                    } else {
                        source =
                                Mono.fromCallable(
                                                () ->
                                                        demoResponder.respond(
                                                                userId,
                                                                conversationId,
                                                                round.getRequestId(),
                                                                message,
                                                                round.getRecent()))
                                        .map(TextChunk::delta)
                                        .flux();
                    }

                    // 模型边生成边发送。这里同时收集最终正文；工具进度只展示，不拼入正文。
                    StringBuilder content = new StringBuilder();
                    Flux<StreamEventVO> deltas =
                            source.map(
                                    chunk -> {
                                        if (chunk.getType().startsWith("tool_"))
                                            return new StreamEventVO(
                                                    chunk.getType(),
                                                    conversationId,
                                                    chunk.getContent(),
                                                    null);
                                        if ("reset".equals(chunk.getType())) {
                                            content.setLength(0);
                                            return StreamEventVO.reset(conversationId);
                                        }
                                        if (chunk.getContent() != null)
                                            content.append(chunk.getContent());
                                        return StreamEventVO.delta(
                                                conversationId, chunk.getContent());
                                    });

                    // Mono 表示单个结果。fromCallable 延迟执行，确保增量结束后才保存完整回复。
                    Mono<StreamEventVO> done =
                            Mono.fromCallable(
                                    () -> {
                                        String answer = content.toString();
                                        if (answer.isBlank()) answer = "我暂时没有生成有效回答，请换个说法再试。";
                                        return StreamEventVO.done(
                                                completeRound(conversationId, answer));
                                    });

                    // concat 保证页面收到的顺序是：开始 → 多次增量/工具进度 → 最终完成。
                    return Flux.concat(Mono.just(StreamEventVO.start(conversationId)), deltas, done)
                            .onErrorResume(
                                    error -> {
                                        String failure = "这次请求没有处理成功：" + rootMessage(error);
                                        conversations.addMessage(
                                                conversationId,
                                                "assistant",
                                                failure,
                                                Map.of("streamError", true));
                                        return Flux.just(
                                                StreamEventVO.error(conversationId, failure));
                                    });
                });
    }

    /** 保存本轮用户消息、按需整理旧摘要，再读取最近历史并生成请求编号。 */
    private ChatRound prepareRound(String conversationId, String message) {
        conversations.addMessage(conversationId, "user", message, Map.of());
        summaryService.compactIfNeeded(conversationId);
        return new ChatRound(
                UUID.randomUUID().toString(),
                conversations.recentMessages(conversationId, properties.getRecentMessageLimit()));
    }

    /** 如果产生待确认操作，用后端确认文案覆盖模型措辞，再保存最终回复和确认卡关联。 */
    private AgentReplyVO completeRound(String conversationId, String content) {
        PendingActionDO pending = pendingActions.latest(conversationId);
        // 确认提示以数据库中真实的待确认操作为准，不能相信模型自行声称“已经下单”。
        if (pending != null) content = replies.confirmationMessage(pending);
        Map<String, Object> metadata =
                pending == null ? Map.of() : Map.of("pendingActionId", pending.getId());
        long messageId = conversations.addMessage(conversationId, "assistant", content, metadata);
        summaryService.compactIfNeeded(conversationId);
        return replies.create(conversationId, messageId, content, pending);
    }

    /** 已有操作待确认时只保存提醒，不把新的业务请求继续交给模型执行。 */
    private AgentReplyVO pendingReminder(String conversationId, PendingActionDO pending) {
        String content = "上一步操作还在等待确认，请先确认或取消：" + pending.getSummary();
        long messageId =
                conversations.addMessage(
                        conversationId,
                        "assistant",
                        content,
                        Map.of("pendingActionId", pending.getId()));
        return replies.create(conversationId, messageId, content, pending);
    }

    /** 模型故障时保留已经准备好的确认操作，否则提示稍后再试，不自动进入演示交易。 */
    private String fallbackResponse(String conversationId) {
        PendingActionDO pending = pendingActions.latest(conversationId);
        if (pending != null) {
            return "AI 服务暂时不可用，但操作已经准备完成，请核对下方确认卡：" + pending.getSummary();
        }
        return "AI 服务暂时不可用，本次未自动转入演示流程。请稍后重试，或通过商城页面继续操作。";
    }

    /** 沿异常原因链找到最内层错误，供日志和失败提示使用。 */
    private static String rootMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? "未知错误" : root.getMessage();
    }

    /** 一轮聊天临时使用的数据。 保存本轮请求编号和最近消息列表，供普通聊天和流式聊天共用，不单独写入数据库。 */
    private static final class ChatRound {
        /** 本轮请求编号，用于追踪调用和生成幂等键。 */
        private final String requestId;

        /** 本轮使用的最近会话消息。 */
        private final List<ChatMessageDO> recent;

        /** 保存这一次内部计算的数据，创建后通过 Getter 读取。 这个内部对象由 Java 代码直接创建，不需要从 HTTP 请求或缓存 JSON 恢复。 */
        public ChatRound(String requestId, List<ChatMessageDO> recent) {
            this.requestId = requestId;
            this.recent = recent;
        }

        /** 获取本轮请求编号，用于追踪调用和生成幂等键。 */
        public String getRequestId() {
            return requestId;
        }

        /** 获取本轮使用的最近会话消息。 */
        public List<ChatMessageDO> getRecent() {
            return recent;
        }

        /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ChatRound that = (ChatRound) other;
            return Objects.equals(requestId, that.requestId) && Objects.equals(recent, that.recent);
        }

        /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
        @Override
        public int hashCode() {
            return Objects.hash(requestId, recent);
        }

        /** 返回字段摘要，供日志和本地调试查看。 */
        @Override
        public String toString() {
            return "ChatRound[" + "requestId=" + requestId + ", recent=" + recent + "]";
        }
    }
}
