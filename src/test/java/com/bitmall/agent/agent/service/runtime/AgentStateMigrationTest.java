package com.bitmall.agent.agent.service.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dao.repository.AgentStateRepository;
import com.bitmall.agent.agent.service.skill.SkillContextPolicy;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import tools.jackson.databind.ObjectMapper;

/** 验证跨框架恢复与中断配对，防止迁移时重放旧交易，或把失败加载结果误当技能授权。 */
class AgentStateMigrationTest {
  @Test
  void newStateRoundTripsToolsMetadataAndKeepsUserIsolation() {
    var repository = mock(AgentStateRepository.class);
    Map<List<String>, String> rows = new HashMap<>();
    doAnswer(
            i -> {
              rows.put(
                  List.of(i.getArgument(0), i.getArgument(1), i.getArgument(2)), i.getArgument(3));
              return null;
            })
        .when(repository)
        .put(anyString(), anyString(), anyString(), anyString());
    when(repository.payload(anyString(), anyString(), anyString()))
        .thenAnswer(
            i ->
                Optional.ofNullable(
                    rows.get(List.of(i.getArgument(0), i.getArgument(1), i.getArgument(2)))));
    var store = new AgentStateStoreService(repository, new ObjectMapper());
    List<Message> history =
        List.of(
            new UserMessage("加载技能"),
            AssistantMessage.builder()
                .content("")
                .toolCalls(
                    List.of(
                        new AssistantMessage.ToolCall(
                            "c1", "function", "load_skill", "{\"skillCode\":\"coupon_ops\"}")))
                .build(),
            ToolResponseMessage.builder()
                .responses(
                    List.of(
                        new ToolResponseMessage.ToolResponse("c1", "load_skill", "{\"ok\":true}")))
                .metadata(Map.of(SkillContextPolicy.LOADED_SKILLS, Map.of("c1", "coupon_ops")))
                .build());
    store.save("1", "session", history);
    assertThat(store.load("1", "session")).isEqualTo(history);
    assertThat(store.load("2", "session")).isEmpty();
    assertThat(rows.keySet()).containsExactly(List.of("1", "session", "spring-ai-v1"));
  }

  @Test
  void interruptedBatchCompletesOnlyMissingResultsBeforeNextUserAndNeverReplays() {
    var builder = new AgentInputBuilder(mock(AgentStateStoreService.class));
    List<Message> history =
        new ArrayList<>(
            List.of(
                new UserMessage("查看"),
                AssistantMessage.builder()
                    .content("")
                    .toolCalls(
                        List.of(
                            new AssistantMessage.ToolCall("a", "function", "order_query", "{}"),
                            new AssistantMessage.ToolCall("b", "function", "order_create", "{}")))
                    .build(),
                ToolResponseMessage.builder()
                    .responses(
                        List.of(new ToolResponseMessage.ToolResponse("a", "order_query", "ok")))
                    .build(),
                new UserMessage("继续")));
    builder.closeInterruptedCalls(history);
    assertThat(history).hasSize(5);
    var interrupted = (ToolResponseMessage) history.get(3);
    assertThat(interrupted.getResponses()).hasSize(1);
    assertThat(interrupted.getResponses().get(0).id()).isEqualTo("b");
    assertThat(interrupted.getResponses().get(0).responseData()).contains("未自动重放");
    assertThat(interrupted.getMetadata()).doesNotContainKey(SkillContextPolicy.LOADED_SKILLS);
    builder.closeInterruptedCalls(history);
    assertThat(history).hasSize(5);
  }

  @Test
  void oldFrameworkPayloadIsNotReadOrDeletedOnFirstSpringAiTurn() {
    var repository = mock(AgentStateRepository.class);
    when(repository.payload("1", "session", AgentStateStoreService.STATE_KEY))
        .thenReturn(Optional.empty());
    var builder = new AgentInputBuilder(new AgentStateStoreService(repository, new ObjectMapper()));
    var recent =
        List.of(
            new ChatMessageDO(
                1L, "session", "user", "上一轮问题", Map.of(), java.time.LocalDateTime.now()),
            new ChatMessageDO(
                2L, "session", "assistant", "上一轮回答", Map.of(), java.time.LocalDateTime.now()),
            new ChatMessageDO(
                3L, "session", "user", "现在的问题", Map.of(), java.time.LocalDateTime.now()));
    var restored = builder.build(1, "session", "现在的问题", recent);
    assertThat(restored).extracting(Message::getText).containsExactly("上一轮问题", "上一轮回答", "现在的问题");
    verify(repository).payload("1", "session", AgentStateStoreService.STATE_KEY);
    verifyNoMoreInteractions(repository);
  }
}
