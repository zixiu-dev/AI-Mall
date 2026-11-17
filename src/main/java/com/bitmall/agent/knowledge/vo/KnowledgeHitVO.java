package com.bitmall.agent.knowledge.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** 可引用的知识命中。保存品牌、类别、出处和适用边界，避免只拿一段正文回答所有品牌。 */
public final class KnowledgeHitVO {
  /** 知识文档编号。 */
  private final long id;

  /** 显示用的中文文档类别。 */
  private final String category;

  /** 知识文档标题。 */
  private final String title;

  /** 供模型引用的摘要正文，属于数据而非指令。 */
  private final String content;

  /** 组内关键词排序分数。 */
  private final int score;

  /** 规范品牌代码或 PLATFORM。 */
  private final String brand;

  /** 规范文档类别。 */
  private final String documentType;

  /** 稳定文档标识。 */
  private final String documentKey;

  /** HTTPS 官方出处，平台内部规则为空。 */
  private final String sourceUrl;

  /** 原始来源名称。 */
  private final String sourceName;

  /** 人工核对日期，不代表政策生效日期。 */
  private final String sourceCheckedAt;

  /** 适用购买渠道。 */
  private final String applicableChannel;

  /** 适用地区。 */
  private final String region;

  /** 本地摘要版本。 */
  private final String contentVersion;

  /** 品牌政策与本平台规则的使用边界。 */
  private final String scopeNote;

  private String chunkId, indexVersion, embeddingModelId, embeddingVersion, textHash;
  private Integer dimension;
  private Double fusionScore;
  private java.util.List<String> retrievalChannels = java.util.List.of("DB_KEYWORD");

  /** 构造一次检索独有的证据副本；原整数 score 仍只代表关键词分数。 */
  public KnowledgeHitVO evidence(
      String chunkId,
      String indexVersion,
      String model,
      String version,
      Integer dimension,
      String hash,
      Double fusionScore,
      java.util.List<String> channels) {
    KnowledgeHitVO copy =
        new KnowledgeHitVO(
            id,
            category,
            title,
            content,
            score,
            brand,
            documentType,
            documentKey,
            sourceUrl,
            sourceName,
            sourceCheckedAt,
            applicableChannel,
            region,
            contentVersion,
            scopeNote);
    copy.chunkId = chunkId;
    copy.indexVersion = indexVersion;
    copy.embeddingModelId = model;
    copy.embeddingVersion = version;
    copy.dimension = dimension;
    copy.textHash = hash;
    copy.fusionScore = fusionScore;
    copy.retrievalChannels = java.util.List.copyOf(channels);
    return copy;
  }

  public String evidenceKey() {
    return documentKey + "|" + contentVersion + "|" + (chunkId == null ? "full" : chunkId);
  }

  public String getChunkId() {
    return chunkId;
  }

  public String getIndexVersion() {
    return indexVersion;
  }

  public String getEmbeddingModelId() {
    return embeddingModelId;
  }

  public String getEmbeddingVersion() {
    return embeddingVersion;
  }

  public Integer getDimension() {
    return dimension;
  }

  public String getTextHash() {
    return textHash;
  }

  public Double getFusionScore() {
    return fusionScore;
  }

  public java.util.List<String> getRetrievalChannels() {
    return retrievalChannels;
  }

  /** 保留旧内部构造入口，新数据库命中使用完整元数据构造。 */
  public KnowledgeHitVO(long id, String category, String title, String content, int score) {
    this(
        id,
        category,
        title,
        content,
        score,
        "PLATFORM",
        "GENERAL",
        null,
        null,
        "沃沃智购体验规则",
        null,
        "本项目体验商城",
        "CN_MAINLAND",
        null,
        "仅作本平台规则说明");
  }

  @JsonCreator
  public KnowledgeHitVO(
      @JsonProperty("id") long id,
      @JsonProperty("category") String category,
      @JsonProperty("title") String title,
      @JsonProperty("content") String content,
      @JsonProperty("score") int score,
      @JsonProperty("brand") String brand,
      @JsonProperty("documentType") String documentType,
      @JsonProperty("documentKey") String documentKey,
      @JsonProperty("sourceUrl") String sourceUrl,
      @JsonProperty("sourceName") String sourceName,
      @JsonProperty("sourceCheckedAt") String sourceCheckedAt,
      @JsonProperty("applicableChannel") String applicableChannel,
      @JsonProperty("region") String region,
      @JsonProperty("contentVersion") String contentVersion,
      @JsonProperty("scopeNote") String scopeNote) {
    this.id = id;
    this.category = category;
    this.title = title;
    this.content = content;
    this.score = score;
    this.brand = brand;
    this.documentType = documentType;
    this.documentKey = documentKey;
    this.sourceUrl = sourceUrl;
    this.sourceName = sourceName;
    this.sourceCheckedAt = sourceCheckedAt;
    this.applicableChannel = applicableChannel;
    this.region = region;
    this.contentVersion = contentVersion;
    this.scopeNote = scopeNote;
  }

  /** 获取知识文档编号。 */
  public long getId() {
    return id;
  }

  /** 获取显示用的中文文档类别。 */
  public String getCategory() {
    return category;
  }

  /** 获取知识文档标题。 */
  public String getTitle() {
    return title;
  }

  /** 获取供模型引用的摘要正文，属于数据而非指令。 */
  public String getContent() {
    return content;
  }

  /** 获取组内关键词排序分数。 */
  public int getScore() {
    return score;
  }

  /** 获取规范品牌代码或 PLATFORM。 */
  public String getBrand() {
    return brand;
  }

  /** 获取规范文档类别。 */
  public String getDocumentType() {
    return documentType;
  }

  /** 获取稳定文档标识。 */
  public String getDocumentKey() {
    return documentKey;
  }

  /** 获取HTTPS 官方出处，平台内部规则为空。 */
  public String getSourceUrl() {
    return sourceUrl;
  }

  /** 获取原始来源名称。 */
  public String getSourceName() {
    return sourceName;
  }

  /** 获取人工核对日期，不代表政策生效日期。 */
  public String getSourceCheckedAt() {
    return sourceCheckedAt;
  }

  /** 获取适用购买渠道。 */
  public String getApplicableChannel() {
    return applicableChannel;
  }

  /** 获取适用地区。 */
  public String getRegion() {
    return region;
  }

  /** 获取本地摘要版本。 */
  public String getContentVersion() {
    return contentVersion;
  }

  /** 获取品牌政策与本平台规则的使用边界。 */
  public String getScopeNote() {
    return scopeNote;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof KnowledgeHitVO that)) return false;
    return id == that.id
        && Objects.equals(category, that.category)
        && Objects.equals(title, that.title)
        && Objects.equals(content, that.content)
        && score == that.score
        && Objects.equals(brand, that.brand)
        && Objects.equals(documentType, that.documentType)
        && Objects.equals(documentKey, that.documentKey)
        && Objects.equals(sourceUrl, that.sourceUrl)
        && Objects.equals(sourceName, that.sourceName)
        && Objects.equals(sourceCheckedAt, that.sourceCheckedAt)
        && Objects.equals(applicableChannel, that.applicableChannel)
        && Objects.equals(region, that.region)
        && Objects.equals(contentVersion, that.contentVersion)
        && Objects.equals(scopeNote, that.scopeNote);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        category,
        title,
        content,
        score,
        brand,
        documentType,
        documentKey,
        sourceUrl,
        sourceName,
        sourceCheckedAt,
        applicableChannel,
        region,
        contentVersion,
        scopeNote);
  }

  @Override
  public String toString() {
    return "KnowledgeHitVO[documentKey="
        + documentKey
        + ",brand="
        + brand
        + ",documentType="
        + documentType
        + ",sourceUrl="
        + sourceUrl
        + ",content="
        + content
        + "]";
  }
}
