package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.common.config.AiModelEndpoints;
import com.bitmall.agent.knowledge.config.KnowledgeProperties;
import com.bitmall.agent.knowledge.model.KnowledgeVectors;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.embedding.*;
import org.springframework.ai.openai.*;
import org.springframework.stereotype.Component;

/** Spring AI EmbeddingModel 适配器。验证模型身份、响应顺序和向量维度后，才允许进入版本化索引。 */
@Component
public class SpringAiEmbeddingClient implements EmbeddingClient {
  private final KnowledgeProperties properties;
  private volatile EmbeddingModel model;

  public SpringAiEmbeddingClient(KnowledgeProperties properties) {
    this.properties = properties;
  }

  /** 懒初始化确保 KEYWORD 模式无需模型凭据；连接复用，调用超时使用本次检索剩余预算。 */
  private EmbeddingModel model() {
    if (model == null)
      synchronized (this) {
        if (model == null) {
          if (properties.getEmbeddingBaseUrl().isBlank()
              || properties.getEmbeddingModel().isBlank()
              || properties.getEmbeddingVersion().isBlank()
              || properties.getEmbeddingDimension() < 1)
            throw new IllegalStateException("真实 embedding 配置缺失：请设置地址、模型、版本和维度");
          model = new OpenAiEmbeddingModel(options(Duration.ofSeconds(8)));
        }
      }
    return model;
  }

  private OpenAiEmbeddingOptions options(Duration timeout) {
    return OpenAiEmbeddingOptions.builder()
        .baseUrl(
            AiModelEndpoints.apiBase(
                properties.getEmbeddingBaseUrl(), properties.getEmbeddingPath(), "/embeddings"))
        .apiKey(
            properties.getEmbeddingApiKey().isBlank()
                ? "not-configured"
                : properties.getEmbeddingApiKey())
        .model(properties.getEmbeddingModel())
        .dimensions(properties.getEmbeddingDimension())
        .encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
        .timeout(timeout)
        .maxRetries(0)
        .build();
  }

  @Override
  public List<double[]> embed(List<String> texts, long deadlineNanos) {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0) throw new IllegalStateException("embedding 截止时间已到");
    try {
      EmbeddingResponse response =
          model().call(new EmbeddingRequest(texts, options(Duration.ofNanos(remaining))));
      if (!properties.getEmbeddingModel().equals(response.getMetadata().getModel()))
        throw new IllegalStateException("embedding 响应模型身份不一致");
      if (response.getResults().size() != texts.size())
        throw new IllegalStateException("embedding 响应数量不符");
      double[][] vectors = new double[texts.size()][];
      for (Embedding item : response.getResults()) {
        int index = item.getIndex();
        if (index < 0 || index >= vectors.length || vectors[index] != null)
          throw new IllegalStateException("embedding 响应索引错误");
        float[] value = item.getOutput();
        double[] converted = new double[value.length];
        for (int i = 0; i < value.length; i++) converted[i] = value[i];
        KnowledgeVectors.validate(converted, properties.getEmbeddingDimension());
        vectors[index] = converted;
      }
      return Arrays.asList(vectors);
    } catch (Exception e) {
      // SDK 错误可能包含供应商响应正文，不传递正文或凭据到检索降级提示。
      throw new IllegalStateException("embedding 调用失败（" + e.getClass().getSimpleName() + "）");
    }
  }
}
