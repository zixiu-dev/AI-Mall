package com.bitmall.agent.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** knowledge_document 表的可写持久化对象，不直接作为接口返回。 */
@TableName("knowledge_document")
public class KnowledgeEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("category")
  private String category;

  @TableField("title")
  private String title;

  @TableField("keywords")
  private String keywords;

  @TableField("content")
  private String content;

  @TableField("enabled")
  private Boolean enabled;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  /** 规范品牌代码。 */
  @TableField("brand_code")
  private String brandCode;

  public String getBrandCode() {
    return brandCode;
  }

  public void setBrandCode(String value) {
    this.brandCode = value;
  }

  /** 规范文档类别。 */
  @TableField("document_type")
  private String documentType;

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String value) {
    this.documentType = value;
  }

  /** 稳定文档标识。 */
  @TableField("document_key")
  private String documentKey;

  public String getDocumentKey() {
    return documentKey;
  }

  public void setDocumentKey(String value) {
    this.documentKey = value;
  }

  /** HTTPS 原始来源。 */
  @TableField("source_url")
  private String sourceUrl;

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String value) {
    this.sourceUrl = value;
  }

  /** 来源名称。 */
  @TableField("source_name")
  private String sourceName;

  public String getSourceName() {
    return sourceName;
  }

  public void setSourceName(String value) {
    this.sourceName = value;
  }

  /** 人工核对日期。 */
  @TableField("source_checked_at")
  private java.time.LocalDate sourceCheckedAt;

  public java.time.LocalDate getSourceCheckedAt() {
    return sourceCheckedAt;
  }

  public void setSourceCheckedAt(java.time.LocalDate value) {
    this.sourceCheckedAt = value;
  }

  /** 适用渠道。 */
  @TableField("applicable_channel")
  private String applicableChannel;

  public String getApplicableChannel() {
    return applicableChannel;
  }

  public void setApplicableChannel(String value) {
    this.applicableChannel = value;
  }

  /** 适用地区。 */
  @TableField("region")
  private String region;

  public String getRegion() {
    return region;
  }

  public void setRegion(String value) {
    this.region = value;
  }

  /** 摘要版本。 */
  @TableField("content_version")
  private String contentVersion;

  public String getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(String value) {
    this.contentVersion = value;
  }

  /** 应用边界。 */
  @TableField("scope_note")
  private String scopeNote;

  public String getScopeNote() {
    return scopeNote;
  }

  public void setScopeNote(String value) {
    this.scopeNote = value;
  }
}
