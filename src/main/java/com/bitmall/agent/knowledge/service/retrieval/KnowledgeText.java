package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** 文本身份及按完整段落切块，避免查询时重建索引或截掉半条适用条件。 */
public final class KnowledgeText {
  private KnowledgeText() {}

  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 数字与型号约束，不把改写新增的天数当成原用户事实。 */
  public static java.util.Set<String> identifiers(String text) {
    return java.util.regex.Pattern.compile(
            "(?i)[a-z]*[0-9]+(?:[.][0-9]+)?[a-z0-9-]*|[零〇一二三四五六七八九十百千万两]+(?:个)?(?:天|日|年|月|小时|分钟)")
        .matcher(text)
        .results()
        .map(m -> m.group().toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.toSet());
  }

  public static String documentHash(KnowledgeHitVO d) {
    return hash(
        d.getId()
            + "|"
            + d.getDocumentKey()
            + "|"
            + d.getContentVersion()
            + "|"
            + d.getBrand()
            + "|"
            + d.getDocumentType()
            + "|"
            + d.getRegion()
            + "|"
            + d.getTitle()
            + "|"
            + d.getContent()
            + "|"
            + d.getSourceUrl()
            + "|"
            + d.getSourceName()
            + "|"
            + d.getSourceCheckedAt()
            + "|"
            + d.getApplicableChannel()
            + "|"
            + d.getScopeNote());
  }

  public static List<String> chunks(String text) {
    if (text == null || text.isBlank() || text.length() > 12000)
      throw new IllegalArgumentException("知识正文为空或超过离线构建上限");
    if (text.length() <= 1200) return List.of(text);
    List<String> out = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    for (String p : text.split("\\n\\s*\\n")) {
      if (block.length() > 0 && block.length() + p.length() > 1200) {
        out.add(block.toString());
        block.setLength(0);
      }
      if (block.length() > 0) block.append("\n\n");
      block.append(p);
    }
    if (block.length() > 0) out.add(block.toString());
    return out;
  }

  public static KnowledgeHitVO withContent(KnowledgeHitVO d, String content, int score) {
    return new KnowledgeHitVO(
        d.getId(),
        d.getCategory(),
        d.getTitle(),
        content,
        score,
        d.getBrand(),
        d.getDocumentType(),
        d.getDocumentKey(),
        d.getSourceUrl(),
        d.getSourceName(),
        d.getSourceCheckedAt(),
        d.getApplicableChannel(),
        d.getRegion(),
        d.getContentVersion(),
        d.getScopeNote());
  }
}
