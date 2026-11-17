package com.bitmall.agent.agent.service.state;

import com.bitmall.agent.agent.dao.repository.AgentStateRepository;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 保存 Spring AI 完整消息及工具配对。显式 JSON 编码隔离 SDK 版本，旧状态保留在原键下，不重放旧调用。 */
@Service
public class AgentStateStoreService {
  public static final String STATE_KEY = "spring-ai-v1";
  private final AgentStateRepository repository;
  private final ObjectMapper json;

  public AgentStateStoreService(AgentStateRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  /** 用户与会话共同定位状态，不能只按模型提供的 conversationId 读取。 */
  public List<Message> load(String user, String session) {
    return repository
        .payload(user, session, STATE_KEY)
        .map(
            payload -> {
              try {
                List<Message> messages = new ArrayList<>();
                for (JsonNode item : json.readTree(payload)) {
                  String text = item.path("text").asText("");
                  Map<String, Object> metadata =
                      json.convertValue(item.path("metadata"), new TypeReference<>() {});
                  switch (item.path("role").asText()) {
                    case "USER" ->
                        messages.add(UserMessage.builder().text(text).metadata(metadata).build());
                    case "SYSTEM" -> messages.add(new SystemMessage(text));
                    case "ASSISTANT" -> {
                      List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                      for (JsonNode c : item.path("calls"))
                        calls.add(
                            new AssistantMessage.ToolCall(
                                c.path("id").asText(),
                                "function",
                                c.path("name").asText(),
                                c.path("arguments").asText()));
                      messages.add(
                          AssistantMessage.builder()
                              .content(text)
                              .properties(metadata)
                              .toolCalls(calls)
                              .build());
                    }
                    case "TOOL" -> {
                      List<ToolResponseMessage.ToolResponse> results = new ArrayList<>();
                      for (JsonNode r : item.path("responses"))
                        results.add(
                            new ToolResponseMessage.ToolResponse(
                                r.path("id").asText(),
                                r.path("name").asText(),
                                r.path("data").asText()));
                      messages.add(
                          ToolResponseMessage.builder()
                              .responses(results)
                              .metadata(metadata)
                              .build());
                    }
                    default -> throw new IllegalStateException("未知消息类型");
                  }
                }
                return messages;
              } catch (Exception e) {
                throw new IllegalStateException("会话状态无法恢复，请核对原会话", e);
              }
            })
        .orElseGet(ArrayList::new);
  }

  /** 在工具执行前保存调用，执行后保存结果；中断后可识别缺失结果，不能据此自动重试。 */
  public void save(String user, String session, List<Message> messages) {
    try {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Message m : messages) {
        // 系统提示每轮重新生成，避免持久化过期的技能、摘要或运行指令。
        if (m.getMessageType() == MessageType.SYSTEM) continue;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", m.getMessageType().name());
        row.put("text", Objects.toString(m.getText(), ""));
        row.put("metadata", m.getMetadata());
        if (m instanceof AssistantMessage a)
          row.put(
              "calls",
              a.getToolCalls().stream()
                  .map(c -> Map.of("id", c.id(), "name", c.name(), "arguments", c.arguments()))
                  .toList());
        if (m instanceof ToolResponseMessage t)
          row.put(
              "responses",
              t.getResponses().stream()
                  .map(r -> Map.of("id", r.id(), "name", r.name(), "data", r.responseData()))
                  .toList());
        rows.add(row);
      }
      repository.put(user, session, STATE_KEY, json.writeValueAsString(rows));
    } catch (Exception e) {
      throw new IllegalStateException("会话状态保存失败", e);
    }
  }

  public boolean exists(String user, String session) {
    return repository.payload(user, session, STATE_KEY).isPresent();
  }
}
