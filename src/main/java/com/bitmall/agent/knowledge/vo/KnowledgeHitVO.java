package com.bitmall.agent.knowledge.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.List;
import lombok.Getter;

/** 可引用的知识命中。保存品牌、类别、出处和适用边界，避免只拿一段正文回答所有品牌。 */
@Getter
public final class KnowledgeHitVO {
  private final long id;

  /** 显示用的中文文档类别。 */
  private final String category;

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

  private final String sourceName;

  /** 人工核对日期，不代表政策生效日期。 */
  private final String sourceCheckedAt;

  private final String applicableChannel;

  private final String region;

  /** 本地摘要版本。 */
  private final String contentVersion;

  /** 品牌政策与本平台规则的使用边界。 */
  private final String scopeNote;

  private String chunkId;
  private String indexVersion;
  private String embeddingModelId;
  private String embeddingVersion;
  private String textHash;
  private Integer dimension;
  private Double fusionScore;
  private List<String> retrievalChannels = List.of("DB_KEYWORD");

  /** 构造一次检索独有的证据副本；原整数 score 仍只代表关键词分数。 */
  public KnowledgeHitVO evidence(
      String chunkId,
      String indexVersion,
      String model,
      String version,
      Integer dimension,
      String hash,
      Double fusionScore,
      List<String> channels) {
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
    copy.retrievalChannels = List.copyOf(channels);
    return copy;
  }

  public String evidenceKey() {
    return documentKey + "|" + contentVersion + "|" + (chunkId == null ? "full" : chunkId);
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

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof KnowledgeHitVO that)) {
      return false;
    }
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
