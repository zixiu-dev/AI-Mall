package com.bitmall.agent.agent.gateway.model;

import com.bitmall.agent.agent.service.runtime.AgentExecution;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Spring AI 流式协议适配层。同步读取文本片段，用 SDK 合并拆分的工具参数，退出时关闭上游订阅。 */
@Component
public class SpringAiStreamReader {
  /** Spring AI 聊天客户端，以流式方式调用模型。 */
  private final ChatClient client;

  public SpringAiStreamReader(@Qualifier("commerceChatClient") ChatClient client) {
    this.client = client;
  }

  /** SDK 使用响应式流接收 HTTP SSE，业务层只接触普通循环和回调。 */
  public ChatResponse read(
      List<Message> messages,
      List<ToolCallback> tools,
      AgentExecution execution,
      Consumer<String> text) {
    AtomicReference<ChatResponse> completed = new AtomicReference<>();
    try (Stream<ChatResponse> stream =
        new MessageAggregator()
            .aggregate(
                client
                    .prompt()
                    .messages(messages)
                    .options(ToolCallingChatOptions.builder().toolCallbacks(tools))
                    .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                    .stream()
                    .chatResponse(),
                completed::set)
            .toStream(1)) {
      Iterator<ChatResponse> iterator = stream.iterator();
      while (iterator.hasNext()) {
        execution.check();
        ChatResponse chunk = iterator.next();
        if (chunk.getResult() != null && chunk.getResult().getOutput().getText() != null) {
          text.accept(chunk.getResult().getOutput().getText());
        }
      }
    }
    execution.check();
    ChatResponse result = completed.get();
    if (result == null || result.getResult() == null) {
      throw new IncompleteResponseException();
    }
    var generation = result.getResult();
    var output = generation.getOutput();
    String finish = generation.getMetadata().getFinishReason();
    if ("LENGTH".equalsIgnoreCase(finish) || "CONTENT_FILTER".equalsIgnoreCase(finish)) {
      throw new IllegalStateException("模型输出达到长度限制或被过滤");
    }
    if (output.getMetadata().get("refusal") instanceof String refusal && !refusal.isBlank()) {
      throw new IllegalStateException("模型拒绝生成当前请求的内容");
    }
    // OpenAiChatModel 在每段响应的消息元数据中标记结束原因字段。
    // TCP 正常结束也可能缺失结束原因，不能把这种半截工具请求交给业务。
    if ((output.getMetadata().containsKey("finishReason") && (finish == null || finish.isBlank()))
        || ("TOOL_CALLS".equalsIgnoreCase(finish) && !output.hasToolCalls())
        || (!output.hasToolCalls() && (output.getText() == null || output.getText().isBlank()))
        || output.getToolCalls().stream().anyMatch(call -> call.id() == null || call.id().isBlank()
            || call.name() == null || call.name().isBlank())) {
      throw new IncompleteResponseException();
    }
    return result;
  }

  /** 没有完整可用结果，允许模型网关切换备用候选。 */
  public static final class IncompleteResponseException extends IllegalStateException {
    private IncompleteResponseException() {
      super("模型未返回完整有效的结果");
    }
  }
}
