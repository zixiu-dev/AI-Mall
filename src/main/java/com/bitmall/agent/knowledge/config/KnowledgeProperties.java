package com.bitmall.agent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 小型政策库的检索配置；模型身份必须显式提供，默认只运行数据库关键词路径。 */
@Component
@ConfigurationProperties("app.knowledge")
public class KnowledgeProperties {
  private String mode = "KEYWORD";
  private long timeoutMillis = 8000;
  private int workers = 4;
  private int maximumChunks = 1000;
  private int candidateLimit = 10;
  private int rrfK = 60;
  private int evidencePerGroup = 2;
  private int maximumCalls = 8;
  private boolean enhancementEnabled;
  private String embeddingBaseUrl = "";
  private String embeddingPath = "/v1/embeddings";
  private String embeddingApiKey = "";
  private String embeddingModel = "";
  private String embeddingVersion = "";
  private int embeddingDimension;
  private int embeddingBatchSize = 8;

  /** 启动时拒绝无界预算，避免错误配置占满连接池或绕过工具时限。 */
  @jakarta.annotation.PostConstruct
  public void validate() {
    if (!java.util.List.of("KEYWORD", "HYBRID").contains(mode)
        || timeoutMillis < 100
        || timeoutMillis > 10000
        || workers < 1
        || workers > 8
        || maximumChunks < 1
        || maximumChunks > 10000
        || candidateLimit < 1
        || candidateLimit > 10
        || embeddingBatchSize < 1
        || embeddingBatchSize > 10
        || rrfK < 1
        || evidencePerGroup < 1
        || evidencePerGroup > 2
        || maximumCalls < 1
        || maximumCalls > 12) throw new IllegalArgumentException("知识检索配置超出有界预算");
  }

  public int getEmbeddingBatchSize() {
    return embeddingBatchSize;
  }

  public void setEmbeddingBatchSize(int size) {
    embeddingBatchSize = size;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String v) {
    mode = v;
  }

  public long getTimeoutMillis() {
    return timeoutMillis;
  }

  public void setTimeoutMillis(long v) {
    timeoutMillis = v;
  }

  public int getWorkers() {
    return workers;
  }

  public void setWorkers(int v) {
    workers = v;
  }

  public int getMaximumChunks() {
    return maximumChunks;
  }

  public void setMaximumChunks(int v) {
    maximumChunks = v;
  }

  public int getCandidateLimit() {
    return candidateLimit;
  }

  public void setCandidateLimit(int v) {
    candidateLimit = v;
  }

  public int getRrfK() {
    return rrfK;
  }

  public void setRrfK(int v) {
    rrfK = v;
  }

  public int getEvidencePerGroup() {
    return evidencePerGroup;
  }

  public void setEvidencePerGroup(int v) {
    evidencePerGroup = v;
  }

  public int getMaximumCalls() {
    return maximumCalls;
  }

  public void setMaximumCalls(int v) {
    maximumCalls = v;
  }

  public boolean isEnhancementEnabled() {
    return enhancementEnabled;
  }

  public void setEnhancementEnabled(boolean v) {
    enhancementEnabled = v;
  }

  public String getEmbeddingBaseUrl() {
    return embeddingBaseUrl;
  }

  public void setEmbeddingBaseUrl(String v) {
    embeddingBaseUrl = v;
  }

  public String getEmbeddingPath() {
    return embeddingPath;
  }

  public void setEmbeddingPath(String v) {
    embeddingPath = v;
  }

  public String getEmbeddingApiKey() {
    return embeddingApiKey;
  }

  public void setEmbeddingApiKey(String v) {
    embeddingApiKey = v;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String v) {
    embeddingModel = v;
  }

  public String getEmbeddingVersion() {
    return embeddingVersion;
  }

  public void setEmbeddingVersion(String v) {
    embeddingVersion = v;
  }

  public int getEmbeddingDimension() {
    return embeddingDimension;
  }

  public void setEmbeddingDimension(int v) {
    embeddingDimension = v;
  }
}
