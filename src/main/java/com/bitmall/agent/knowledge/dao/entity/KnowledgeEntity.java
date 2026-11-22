package com.bitmall.agent.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** knowledge_document 表的可写持久化对象，不直接作为接口返回。 */
@TableName("knowledge_document")
@Getter
@Setter
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

  /** 规范品牌代码。 */
  @TableField("brand_code")
  private String brandCode;

  /** 规范文档类别。 */
  @TableField("document_type")
  private String documentType;

  /** 稳定文档标识。 */
  @TableField("document_key")
  private String documentKey;

  /** HTTPS 原始来源。 */
  @TableField("source_url")
  private String sourceUrl;

  @TableField("source_name")
  private String sourceName;

  /** 人工核对日期。 */
  @TableField("source_checked_at")
  private LocalDate sourceCheckedAt;

  @TableField("applicable_channel")
  private String applicableChannel;

  @TableField("region")
  private String region;

  /** 摘要版本。 */
  @TableField("content_version")
  private String contentVersion;

  @TableField("scope_note")
  private String scopeNote;
}
