package com.bitmall.agent.agent.service.state;

import com.bitmall.agent.agent.dao.repository.AgentStateRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 保存和恢复每个会话的模型上下文（完整的 Spring AI 消息，包括工具调用和工具结果）。
 *
 * <p>存储格式是我们自己定义的 JSON（每条消息一行：role、text、metadata、calls、responses）， 不直接序列化 Spring AI 的对象，这样升级 SDK
 * 版本时已保存的状态仍然能读。旧版本的状态存在另一个 key 下，这里不读取，也不重放旧的工具调用。
 */
@Service
@RequiredArgsConstructor
public class AgentStateStoreService {
  /** 当前格式在状态表中的 key。 */
  public static final String STATE_KEY = "spring-ai-v1";

  /** 按用户 + 会话读写模型上下文 JSON。 */
  private final AgentStateRepository repository;

  /** 把 Spring AI 消息与可持久化的 JSON 互相转换。 */
  private final ObjectMapper json;

  /**
   * 恢复会话的模型上下文；没有保存过时返回空列表。
   *
   * <p>用用户和会话一起定位，不能只凭模型传来的 conversationId 读取，否则可能读到别人的会话。
   */
  public List<Message> load(String user, String session) {
    return repository.payload(user, session, STATE_KEY).map(this::parse).orElseGet(ArrayList::new);
  }

  private List<Message> parse(String payload) {
    try {
      List<Message> messages = new ArrayList<>();
      for (JsonNode item : json.readTree(payload)) {
        messages.add(toMessage(item));
      }
      return messages;
    } catch (Exception e) {
      throw new IllegalStateException("会话状态无法恢复，请核对原会话", e);
    }
  }

  private Message toMessage(JsonNode item) {
    String text = item.path("text").asText("");
    Map<String, Object> metadata =
        json.convertValue(item.path("metadata"), new TypeReference<>() {});
    return switch (item.path("role").asText()) {
      case "USER" -> UserMessage.builder().text(text).metadata(metadata).build();
      case "SYSTEM" -> new SystemMessage(text);
      case "ASSISTANT" ->
          AssistantMessage.builder()
              .content(text)
              .properties(metadata)
              .toolCalls(toolCalls(item.path("calls")))
              .build();
      case "TOOL" ->
          ToolResponseMessage.builder()
              .responses(toolResponses(item.path("responses")))
              .metadata(metadata)
              .build();
      default -> throw new IllegalStateException("未知消息类型");
    };
  }

  private static List<AssistantMessage.ToolCall> toolCalls(JsonNode calls) {
    List<AssistantMessage.ToolCall> result = new ArrayList<>();
    for (JsonNode call : calls) {
      result.add(
          new AssistantMessage.ToolCall(
              call.path("id").asText(),
              "function",
              call.path("name").asText(),
              call.path("arguments").asText()));
    }
    return result;
  }

  private static List<ToolResponseMessage.ToolResponse> toolResponses(JsonNode responses) {
    List<ToolResponseMessage.ToolResponse> result = new ArrayList<>();
    for (JsonNode response : responses) {
      result.add(
          new ToolResponseMessage.ToolResponse(
              response.path("id").asText(),
              response.path("name").asText(),
              response.path("data").asText()));
    }
    return result;
  }

  /**
   * 保存会话的模型上下文。调用方在工具执行前先保存调用，执行后再保存结果，所以中断后能看出哪些调用没有结果 （见
   * AgentInputBuilder.closeInterruptedCalls），但不会据此自动重试。
   *
   * <p>系统消息不保存：系统提示每轮重新生成，保存下来反而会带入过期的技能、摘要或运行指令。
   */
  public void save(String user, String session, List<Message> messages) {
    try {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Message message : messages) {
        if (message.getMessageType() != MessageType.SYSTEM) {
          rows.add(toRow(message));
        }
      }
      repository.put(user, session, STATE_KEY, json.writeValueAsString(rows));
    } catch (Exception e) {
      throw new IllegalStateException("会话状态保存失败", e);
    }
  }

  private static Map<String, Object> toRow(Message message) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("role", message.getMessageType().name());
    row.put("text", Objects.toString(message.getText(), ""));
    row.put("metadata", message.getMetadata());
    if (message instanceof AssistantMessage assistant) {
      row.put(
          "calls",
          assistant.getToolCalls().stream()
              .map(
                  call ->
                      Map.of("id", call.id(), "name", call.name(), "arguments", call.arguments()))
              .toList());
    }
    if (message instanceof ToolResponseMessage toolResults) {
      row.put(
          "responses",
          toolResults.getResponses().stream()
              .map(
                  response ->
                      Map.of(
                          "id",
                          response.id(),
                          "name",
                          response.name(),
                          "data",
                          response.responseData()))
              .toList());
    }
    return row;
  }

  public boolean exists(String user, String session) {
    return repository.payload(user, session, STATE_KEY).isPresent();
  }
}
