package com.bitmall.agent.agent.service.chat;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.AgentAnswerDO;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.repository.ConversationRepository;
import com.bitmall.agent.agent.dto.AgentTurnRequest;
import com.bitmall.agent.agent.gateway.model.ModelUnavailableException;
import com.bitmall.agent.agent.service.conversation.ConversationService;
import com.bitmall.agent.agent.service.runtime.AgentExecution;
import com.bitmall.agent.agent.service.runtime.AgentRunGate;
import com.bitmall.agent.agent.service.trace.AgentExecutionJournal;
import com.bitmall.agent.agent.vo.AgentExecutionVO;
import com.bitmall.agent.agent.vo.AgentTurnSummaryVO;
import com.bitmall.agent.agent.vo.AgentTurnVO;
import com.bitmall.agent.agent.vo.StreamEventVO;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 提交、执行与查询可重连的聊天轮次，数据库保留助手消息终态。 */
@Service
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class AgentTurnService {
  private static final int RECENT_TURNS_LIMIT = 30;
  private static final Logger log = LoggerFactory.getLogger(AgentTurnService.class);
  private static final String FAILED = "FAILED";
  private static final String GENERATING = "GENERATING";
  private static final String GENERATION_FAILURE = "这次回答没有生成完成，请稍后重新发送。";

  /** 在一个短事务保存问题与助手占位，按用户读取原回答。 */
  private final ConversationService conversations;

  /** 启动时结束上一进程遗留的占位。 */
  private final ConversationRepository messages;

  /** 调用现有模型循环，生成并保存完整的助手回复。 */
  private final AgentChatService chats;

  /** 聊天、确认与后台轮次共用会话门禁。 */
  private final AgentRunGate gate;

  /** 执行已提交会话的有界后台线程池。 */
  @Qualifier("agentChatExecutor")
  private final ThreadPoolTaskExecutor executor;

  /** 逐连接发送事件，避免阻塞模型与工具调用。 */
  @Qualifier("agentStreamSender")
  private final ThreadPoolTaskExecutor sender;

  /** 约束模型与工具执行预算。 */
  private final ScheduledExecutorService scheduler;

  /** 读取聊天总预算，SSE 订阅使用稍长的连接超时。 */
  private final AgentProperties properties;

  /** 创建并查询仅用于排障的非阻塞明细。 */
  private final AgentExecutionJournal journal;

  /** 每条活动助手消息的一份草稿，完成时释放。 */
  private final Map<Long, AgentAnswerStream> streams = new ConcurrentHashMap<>();

  @PostConstruct
  void recoverAfterRestart() {
    messages.interruptUnfinishedAnswers();
  }

  /** 在持有会话执行权期间提交一次问题。重复请求返回原回答，后续不再安排执行。 */
  public AgentTurnVO submit(AgentTurnRequest request) {
    long userId = request.resolvedUserId();
    String requestKey = requestKey(userId, request.getClientRequestId());
    Optional<AgentAnswerDO> recorded = conversations.answerByRequest(userId, requestKey);
    if (recorded.isPresent()) {
      return verifyReplay(request, recorded.get());
    }

    boolean newConversation = request.getConversationId() == null || request.getConversationId().isBlank();
    String conversationId = newConversation
        ? UUID.nameUUIDFromBytes(requestKey.getBytes(StandardCharsets.UTF_8)).toString()
        : request.getConversationId();
    AgentRunGate.Lease lease;
    try {
      lease = gate.acquire(conversationId);
    } catch (IllegalStateException busy) {
      return conversations.answerByRequest(userId, requestKey)
          .map(answer -> verifyReplay(request, answer))
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, busy.getMessage()));
    }

    boolean handedOff = false;
    try {
      // 再查一次避免刚完成的同一轮在释放门禁后被重复提交。
      recorded = conversations.answerByRequest(userId, requestKey);
      if (recorded.isPresent()) {
        return verifyReplay(request, recorded.get());
      }
      AgentAnswerDO answer;
      try {
        answer = conversations.createAnswer(
            userId, conversationId, newConversation, request.getMessage(), requestKey);
      } catch (DuplicateKeyException race) {
        return verifyReplay(request, conversations.answerByRequest(userId, requestKey).orElseThrow(() -> race));
      }
      AgentAnswerStream stream = createStream(conversationId);
      long messageId = answer.getMessage().getId();
      streams.put(messageId, stream);
      var trace = journal.startTurn(messageId);
      var queueWait = trace.beginQueueWait();
      AgentExecution execution = new AgentExecution(scheduler, properties.getRequestTimeoutSeconds(), trace);
      try {
        executor.execute(() -> {
          queueWait.finish("SUCCESS", null);
          run(userId, answer, execution, stream, lease);
        });
      } catch (RejectedExecutionException busy) {
        queueWait.finish("REJECTED", "QUEUE_FULL");
        execution.close();
        try {
          fail(answer.getMessage(), "助手当前较忙，请稍后重新发送。", stream);
        } finally {
          streams.remove(messageId, stream);
        }
        return result(userId, messageId);
      }
      handedOff = true;
      return new AgentTurnVO(answer.getMessage());
    } finally {
      if (!handedOff) {
        lease.close();
      }
    }
  }

  /** 关闭执行对象后记录失败，避免残留中断影响数据库连接。 */
  private void run(long userId, AgentAnswerDO answer, AgentExecution execution,
      AgentAnswerStream stream, AgentRunGate.Lease lease) {
    ChatMessageDO message = answer.getMessage();
    try (lease) {
      try {
        StreamEventVO terminal;
        try (execution) {
          terminal = StreamEventVO.done(chats.generateAnswer(userId, answer, execution, stream::publish));
        } catch (RuntimeException failure) {
          if (failure instanceof ModelUnavailableException && recoverPending(message, stream, execution)) {
            return;
          }
          log.warn("后台回答生成失败，消息编号 {}", message.getId(), failure);
          fail(message, GENERATION_FAILURE, stream);
          return;
        }
        stream.finish(terminal);
      } finally {
        streams.remove(message.getId(), stream);
      }
    }
  }

  /** 已生成确认卡时给用户原回执，核对不可用的情况下沿用普通失败终态。 */
  private boolean recoverPending(ChatMessageDO message, AgentAnswerStream stream, AgentExecution execution) {
    try {
      var reply = chats.recoverPendingAnswer(message);
      if (reply == null) {
        return false;
      }
      execution.trace().confirmationRecovered(reply.getPendingAction().getId());
      stream.finish(StreamEventVO.done(reply));
      return true;
    } catch (RuntimeException failure) {
      log.warn("恢复确认卡失败，消息编号 {}", message.getId(), failure);
      return false;
    }
  }

  private void fail(ChatMessageDO message, String content, AgentAnswerStream stream) {
    conversations.finishAnswer(message.getConversationId(), message.getId(), FAILED, content, Map.of());
    stream.finish(StreamEventVO.error(message.getConversationId(), content));
  }

  public AgentTurnVO result(long userId, long messageId) {
    return new AgentTurnVO(conversations.answer(userId, messageId).getMessage());
  }

  /** 归属校验沿用回答接口，诊断查询只读取原轮次。 */
  public AgentExecutionVO executionDetails(long userId, long messageId) {
    var message = conversations.answer(userId, messageId).getMessage();
    return new AgentExecutionVO(message, journal.find(messageId));
  }

  /** 返回指定用户最近的后台轮次，按助手消息编号从新到旧。 */
  public List<AgentTurnSummaryVO> recentTurns(long userId) {
    return messages.recentTurns(userId, RECENT_TURNS_LIMIT).stream()
        .map(AgentTurnSummaryVO::new)
        .toList();
  }

  /** 找回 POST 回执丢失的回答。GET 仅查已提交记录，刷新不会发起新一轮。 */
  public AgentTurnVO resultByRequest(long userId, String clientRequestId) {
    return conversations.answerByRequest(userId, requestKey(userId, clientRequestId))
        .map(answer -> new AgentTurnVO(answer.getMessage()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "发送请求尚未受理"));
  }

  /** 完成与重连并发时，从活跃流的终态或从已提交的消息恢复全文。 */
  public SseEmitter subscribe(long userId, long messageId) {
    ChatMessageDO message = conversations.answer(userId, messageId).getMessage();
    if (GENERATING.equals(message.getGenerationStatus())) {
      AgentAnswerStream active = streams.get(messageId);
      if (active != null) {
        return active.subscribe();
      }
      message = conversations.answer(userId, messageId).getMessage();
      if (GENERATING.equals(message.getGenerationStatus())) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "回答状态正在同步，请稍后查询");
      }
    }
    AgentAnswerStream completed = createStream(message.getConversationId());
    completed.finish(FAILED.equals(message.getGenerationStatus())
        ? StreamEventVO.error(message.getConversationId(), message.getContent())
        : StreamEventVO.done(chats.savedAnswer(message)));
    return completed.subscribe();
  }

  private AgentAnswerStream createStream(String conversationId) {
    return new AgentAnswerStream(conversationId, sender, (properties.getRequestTimeoutSeconds() + 5L) * 1000L);
  }

  private static String requestKey(long userId, String clientRequestId) {
    return userId + ":" + clientRequestId;
  }

  private static AgentTurnVO verifyReplay(AgentTurnRequest request, AgentAnswerDO original) {
    ChatMessageDO message = original.getMessage();
    boolean differentConversation = request.getConversationId() != null
        && !request.getConversationId().isBlank()
        && !request.getConversationId().equals(message.getConversationId());
    if (differentConversation || !request.getMessage().equals(original.getQuestion())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "同一请求编号不能修改会话或问题");
    }
    return new AgentTurnVO(message);
  }
}
