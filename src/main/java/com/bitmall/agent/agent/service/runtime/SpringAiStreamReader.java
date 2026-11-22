package com.bitmall.agent.agent.service.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import java.util.Iterator;

/** Spring AI 流式协议适配层。同步读取文本片段，用 SDK 合并拆分的工具参数，退出时关闭上游订阅。 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamReader {
  /** Spring AI 聊天客户端，以流式方式调用模型。 */
  private final ChatClient client;

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
      throw new IllegalStateException("模型未返回有效结果");
    }
    return result;
  }
}
