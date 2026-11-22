package com.bitmall.agent.agent.config;

import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.bitmall.agent.common.config.AiModelEndpoints;

/** Spring AI 模型入口。兼容现有 OPENAI_* 地址参数，身份、工具权限和交易确认均由业务服务管理。 */
@Configuration
public class SpringAiConfiguration {
  @Bean
  @ConditionalOnMissingBean(ChatModel.class)
  public ChatModel commerceChatModel(
      @Value("${app.agent.model.api-key}") String key,
      @Value("${app.agent.model.base-url}") String base,
      @Value("${app.agent.model.endpoint-path}") String path,
      @Value("${app.agent.model.name}") String name) {
    return OpenAiChatModel.builder()
        .options(
            OpenAiChatOptions.builder()
                .apiKey(key.isBlank() ? "not-configured" : key)
                .baseUrl(
                    AiModelEndpoints.apiBase(
                        base, path, "/chat/completions"))
                .model(name)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .parallelToolCalls(false)
                .build())
        .build();
  }

  /** 模型门面统一注入；工具循环在每次请求显式关闭默认 Advisor 后由受控服务驱动。 */
  @Bean
  public ChatClient commerceChatClient(ChatModel model) {
    return ChatClient.builder(model).build();
  }
}
