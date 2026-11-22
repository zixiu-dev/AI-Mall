package com.bitmall.agent.knowledge.service.retrieval;

import com.bitmall.agent.knowledge.vo.KnowledgeGroupVO;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 把检索结果裁剪到预算以内。
 *
 * <p>裁剪的最小单位是一整条证据：要么整条保留，要么整条删掉，绝不截断证据正文，
 * 否则可能把政策的限制条件或来源信息切掉。每删一条都重新生成结果对象，
 * 让分组状态、顶层 hits 和覆盖率与剩下的证据保持一致。
 */
public final class KnowledgeEvidencePacker {
  private static final String STATUS_PARTIAL = "PARTIAL";
  private static final String EVIDENCE_BUDGET_WARNING = "EVIDENCE_BUDGET";

  private KnowledgeEvidencePacker() {}

  /** 按证据条数裁剪：顶层 hits 超过 total 条时，不断删除证据直到满足或无可删除。 */
  public static KnowledgeSearchResultVO limit(KnowledgeSearchResultVO source, int total) {
    List<KnowledgeGroupVO> groups = new ArrayList<>(source.getGroups());
    while (new KnowledgeSearchResultVO(groups, source.getWarnings()).getHits().size() > total) {
      boolean removed = removeOneHit(groups);
      if (!removed) {
        break;
      }
    }
    return new KnowledgeSearchResultVO(groups, source.getWarnings());
  }

  /**
   * 按序列化后的字符数裁剪，供 Agent 工具返回使用。
   *
   * <p>预算按最终发给模型的 JSON 计算，即 {"ok":true,"data":...} 整体的长度。同一条证据既出现在
   * groups 里，也出现在兼容旧调用方的顶层 hits 里，两处都算真实字符数。
   *
   * <p>每轮的处理：
   * <ol>
   *   <li>超预算时先去掉各分组的 variants 和空的 resolvedContextReferences——模型本来就知道自己传了什么，
   *       这些是重复信息；政策来源字段一律不删。
   *   <li>只要已经删过证据，就在结果里标记 truncated / PARTIAL，提醒模型结论不完整。
   *   <li>仍超预算则删一条证据进入下一轮；已无证据可删时抛异常。
   * </ol>
   */
  public static Object pack(KnowledgeSearchResultVO source, ObjectMapper json, int maximum)
      throws Exception {
    List<KnowledgeGroupVO> groups = new ArrayList<>(source.getGroups());
    boolean truncated = false;
    while (true) {
      Map<String, Object> output =
          json.convertValue(
              new KnowledgeSearchResultVO(groups, source.getWarnings()),
              new TypeReference<Map<String, Object>>() {});
      if (serializedLength(json, output) > maximum) {
        dropRedundantGroupFields(output);
      }
      if (truncated) {
        output.put("truncated", true);
        output.put("status", STATUS_PARTIAL);
        output.put("message", "部分完整证据因上下文预算未展示，请按品牌/类别分别继续检索，不能将已展示政策当作全部结论。");
      }
      if (serializedLength(json, output) <= maximum) {
        return output;
      }
      boolean removed = removeOneHit(groups);
      if (!removed) {
        throw new IllegalStateException("知识分组元数据超过上下文预算，请缩小问题范围后分别检索");
      }
      truncated = true;
    }
  }

  /** 工具最终返回给模型的 JSON 长度。 */
  private static int serializedLength(ObjectMapper json, Map<String, Object> output)
      throws Exception {
    return json.writeValueAsString(Map.of("ok", true, "data", output)).length();
  }

  /** 去掉每个分组的 variants，以及为空的 resolvedContextReferences。 */
  private static void dropRedundantGroupFields(Map<String, Object> output) {
    for (Object item : (List<?>) output.get("groups")) {
      if (item instanceof Map<?, ?> raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> group = (Map<String, Object>) raw;
        group.remove("variants");
        boolean noReferences = ((List<?>) group.get("resolvedContextReferences")).isEmpty();
        if (noReferences) {
          group.remove("resolvedContextReferences");
        }
      }
    }
  }

  /**
   * 删除一条证据：从证据最多的分组里删掉最后一条（排名最低的）。
   *
   * <p>总是从最多的分组下手，是为了尽量让每个子问题都至少保留一条完整依据。
   * 证据条数并列时选靠后的分组。被删过的分组标记为 PARTIAL 并带上 EVIDENCE_BUDGET 警告。
   *
   * @return 所有分组都已没有证据、无法再删时返回 false
   */
  private static boolean removeOneHit(List<KnowledgeGroupVO> groups) {
    int selectedIndex = -1;
    int maxHits = 0;
    for (int i = 0; i < groups.size(); i++) {
      int hitCount = groups.get(i).getHits().size();
      if (hitCount >= maxHits && hitCount > 0) {
        selectedIndex = i;
        maxHits = hitCount;
      }
    }
    if (selectedIndex < 0) {
      return false;
    }

    KnowledgeGroupVO group = groups.get(selectedIndex);
    List<KnowledgeHitVO> hits = new ArrayList<>(group.getHits());
    hits.remove(hits.size() - 1);
    List<String> warnings = new ArrayList<>(group.getWarnings());
    if (!warnings.contains(EVIDENCE_BUDGET_WARNING)) {
      warnings.add(EVIDENCE_BUDGET_WARNING);
    }
    groups.set(
        selectedIndex,
        new KnowledgeGroupVO(
            group.getQueryId(),
            group.getQuery(),
            STATUS_PARTIAL,
            "部分证据受预算限制，请分别继续检索。",
            group.getRetrievalMode(),
            group.getIntent(),
            hits,
            warnings,
            group.getVariants(),
            group.getResolvedContextReferences()));
    return true;
  }
}
