package com.bitmall.agent.knowledge.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 政策知识检索配置（app.knowledge）。
 *
 * <p>默认只走数据库关键词检索；向量检索（HYBRID）需要显式配置 embedding 模型的地址、名称、版本和维度。 所有并发、数量和时间预算都有上限，启动时校验，
 * 防止错误配置占满连接池或让单次工具调用超时。
 */
@Getter
@Setter
@Component
@ConfigurationProperties("app.knowledge")
public class KnowledgeProperties {
  /** 检索模式：KEYWORD（只用数据库关键词）或 HYBRID（关键词 + 向量，按 RRF 融合）。默认 KEYWORD。 */
  private String mode = "KEYWORD";

  /** 单次检索请求的超时（毫秒），默认 8000，允许 100～10000。 */
  private long timeoutMillis = 8000;

  /** 检索线程池的线程数，默认 4，允许 1～8。 */
  private int workers = 4;

  /** 索引允许的最大知识块数，默认 1000，允许 1～10000；构建和读取时均校验，限制单次处理规模。 */
  private int maximumChunks = 1000;

  /** 每个检索分支（关键词、向量）召回的候选数，默认 10，允许 1～10。 */
  private int candidateLimit = 10;

  /** RRF 融合公式 1 / (k + 排名) 里的 k，默认 60，允许正整数；越大，排名靠后的结果权重下降得越慢。 */
  private int rrfK = 60;

  /** 每个子问题最终返回的证据条数，默认 2，允许 1～2。 */
  private int evidencePerGroup = 2;

  /** 一轮 Agent 对话里最多调用知识检索的次数（缓存命中也计数），默认 8，允许 1～12；防止模型反复检索耗尽预算。 */
  private int maximumCalls = 8;

  /** 是否用模型改写检索子问题（查询增强），默认关闭。 */
  private boolean enhancementEnabled;

  /** embedding 服务的基础地址；为空时向量检索不可用。 */
  private String embeddingBaseUrl = "";

  /** embedding 接口路径，默认 /v1/embeddings。 */
  private String embeddingPath = "/v1/embeddings";

  /** embedding 服务的 API Key（通过环境变量注入，不要写进配置文件）。 */
  private String embeddingApiKey = "";

  /** embedding 模型名称；记录在索引版本里，模型变了需要重建索引。 */
  private String embeddingModel = "";

  /** embedding 模型版本；同上，用于判断索引是否与当前模型兼容。 */
  private String embeddingVersion = "";

  /** 向量维度，必须与模型实际输出一致。 */
  private int embeddingDimension;

  /** 重建索引时每批送去向量化的文本数，默认 8，允许 1～10。 */
  private int embeddingBatchSize = 8;

  /** 启动时校验各项预算都在允许范围内。 */
  @PostConstruct
  public void validate() {
    boolean modeValid = List.of("KEYWORD", "HYBRID").contains(mode);
    boolean budgetsValid =
        timeoutMillis >= 100
            && timeoutMillis <= 10000
            && workers >= 1
            && workers <= 8
            && maximumChunks >= 1
            && maximumChunks <= 10000
            && candidateLimit >= 1
            && candidateLimit <= 10
            && embeddingBatchSize >= 1
            && embeddingBatchSize <= 10
            && rrfK >= 1
            && evidencePerGroup >= 1
            && evidencePerGroup <= 2
            && maximumCalls >= 1
            && maximumCalls <= 12;
    if (!modeValid || !budgetsValid) {
      throw new IllegalArgumentException("知识检索配置超出有界预算");
    }
  }
}
