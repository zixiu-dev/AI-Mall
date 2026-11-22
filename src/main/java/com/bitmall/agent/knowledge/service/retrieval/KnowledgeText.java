package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 文本身份及按完整段落切块，避免查询时重建索引或截掉半条适用条件。 */
public final class KnowledgeText {
  /** 单篇正文最多接收 12000 个 Java 字符，限制离线构建的输入规模。 */
  private static final int MAX_DOCUMENT_CHARACTERS = 12000;

  /** 目标块长为 1200 个 Java 字符；单个超长段落保留完整，不在条件句中间截断。 */
  private static final int TARGET_CHUNK_CHARACTERS = 1200;

  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String PARAGRAPH_SEPARATOR = "\\n\\s*\\n";
  private static final Pattern IDENTIFIER_PATTERN =
      Pattern.compile(
          "(?i)[a-z]*[0-9]+(?:[.][0-9]+)?[a-z0-9-]*|[零〇一二三四五六七八九十百千万两]+(?:个)?(?:天|日|年|月|小时|分钟)");

  private KnowledgeText() {}

  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance(HASH_ALGORITHM)
                  .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  /** 数字与型号约束，不把改写新增的天数当成原用户事实。 */
  public static Set<String> identifiers(String text) {
    return IDENTIFIER_PATTERN
        .matcher(text)
        .results()
        .map(match -> match.group().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  /** 正文与来源、范围一起参与摘要，任一项变化都会要求重建文档身份。 */
  public static String documentHash(KnowledgeHitVO document) {
    return hash(
        document.getId()
            + "|"
            + document.getDocumentKey()
            + "|"
            + document.getContentVersion()
            + "|"
            + document.getBrand()
            + "|"
            + document.getDocumentType()
            + "|"
            + document.getRegion()
            + "|"
            + document.getTitle()
            + "|"
            + document.getContent()
            + "|"
            + document.getSourceUrl()
            + "|"
            + document.getSourceName()
            + "|"
            + document.getSourceCheckedAt()
            + "|"
            + document.getApplicableChannel()
            + "|"
            + document.getScopeNote());
  }

  /** 按空行划分的段落组块；达到目标长度后另起一块，保留完整段落及段内适用条件。 */
  public static List<String> chunks(String text) {
    if (text == null || text.isBlank() || text.length() > MAX_DOCUMENT_CHARACTERS) {
      throw new IllegalArgumentException("知识正文为空或超过离线构建上限");
    }
    if (text.length() <= TARGET_CHUNK_CHARACTERS) {
      return List.of(text);
    }
    List<String> chunks = new ArrayList<>();
    StringBuilder currentChunk = new StringBuilder();
    for (String paragraph : text.split(PARAGRAPH_SEPARATOR)) {
      if (currentChunk.length() > 0
          && currentChunk.length() + paragraph.length() > TARGET_CHUNK_CHARACTERS) {
        chunks.add(currentChunk.toString());
        currentChunk.setLength(0);
      }
      if (currentChunk.length() > 0) {
        currentChunk.append("\n\n");
      }
      currentChunk.append(paragraph);
    }
    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString());
    }
    return chunks;
  }

  /** 为切片替换正文和检索分数，原文档的来源、版本、适用渠道等信息完整沿用。 */
  public static KnowledgeHitVO withContent(KnowledgeHitVO document, String content, int score) {
    return new KnowledgeHitVO(
        document.getId(),
        document.getCategory(),
        document.getTitle(),
        content,
        score,
        document.getBrand(),
        document.getDocumentType(),
        document.getDocumentKey(),
        document.getSourceUrl(),
        document.getSourceName(),
        document.getSourceCheckedAt(),
        document.getApplicableChannel(),
        document.getRegion(),
        document.getContentVersion(),
        document.getScopeNote());
  }
}
