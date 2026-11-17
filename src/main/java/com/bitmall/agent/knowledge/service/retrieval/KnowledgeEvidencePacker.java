package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.vo.KnowledgeGroupVO;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 按完整证据块打包；删除后重算分组、顶层 hits 和覆盖率，不截断政策条件或来源。 */
public final class KnowledgeEvidencePacker {
  private KnowledgeEvidencePacker() {}

  public static KnowledgeSearchResultVO limit(KnowledgeSearchResultVO source, int total) {
    var groups = new ArrayList<>(source.getGroups());
    while (new KnowledgeSearchResultVO(groups, source.getWarnings()).getHits().size() > total)
      if (!remove(groups)) break;
    return new KnowledgeSearchResultVO(groups, source.getWarnings());
  }

  /** Agent 工具预算包括 JSON 包装；同一份证据在 groups 与兼容 hits 中计入真实字符量。 */
  public static Object pack(KnowledgeSearchResultVO source, ObjectMapper json, int maximum)
      throws Exception {
    var groups = new ArrayList<>(source.getGroups());
    boolean truncated = false;
    Map<String, Object> out;
    while (true) {
      out =
          json.convertValue(
              new KnowledgeSearchResultVO(groups, source.getWarnings()),
              new TypeReference<Map<String, Object>>() {});
      // 模型已持有工具入参；紧预算下省略重复的表达变体和空引用，不删政策来源字段。
      if (json.writeValueAsString(Map.of("ok", true, "data", out)).length() > maximum) {
        for (Object item : (List<?>) out.get("groups"))
          if (item instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            var group = (Map<String, Object>) raw;
            group.remove("variants");
            if (((List<?>) group.get("resolvedContextReferences")).isEmpty())
              group.remove("resolvedContextReferences");
          }
      }
      if (truncated) {
        out.put("truncated", true);
        out.put("status", "PARTIAL");
        out.put("message", "部分完整证据因上下文预算未展示，请按品牌/类别分别继续检索，不能将已展示政策当作全部结论。");
      }
      if (json.writeValueAsString(Map.of("ok", true, "data", out)).length() <= maximum) return out;
      if (!remove(groups)) throw new IllegalStateException("知识分组元数据超过上下文预算，请缩小问题范围后分别检索");
      truncated = true;
    }
  }

  /** 优先移除多证据组的末尾块，尽量让各个子问题都保留至少一条完整依据。 */
  private static boolean remove(List<KnowledgeGroupVO> groups) {
    int selected = -1, max = 0;
    for (int i = 0; i < groups.size(); i++)
      if (groups.get(i).getHits().size() >= max && !groups.get(i).getHits().isEmpty()) {
        selected = i;
        max = groups.get(i).getHits().size();
      }
    if (selected < 0) return false;
    var g = groups.get(selected);
    var hits = new ArrayList<KnowledgeHitVO>(g.getHits());
    hits.remove(hits.size() - 1);
    var warnings = new ArrayList<>(g.getWarnings());
    if (!warnings.contains("EVIDENCE_BUDGET")) warnings.add("EVIDENCE_BUDGET");
    groups.set(
        selected,
        new KnowledgeGroupVO(
            g.getQueryId(),
            g.getQuery(),
            "PARTIAL",
            "部分证据受预算限制，请分别继续检索。",
            g.getRetrievalMode(),
            g.getIntent(),
            hits,
            warnings,
            g.getVariants(),
            g.getResolvedContextReferences()));
    return true;
  }
}
