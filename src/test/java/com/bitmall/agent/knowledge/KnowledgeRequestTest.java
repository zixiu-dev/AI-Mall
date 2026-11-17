package com.bitmall.agent.knowledge;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 工具与 HTTP 共用的执行层契约；不依赖模型一定遵守 Schema。 */
class KnowledgeRequestTest {
  @Test
  void deduplicatesBeforeEffectiveLimit() {
    var r =
        KnowledgeRetrievalRequest.from(
            Map.of(
                "query",
                "比较政策",
                "queries",
                List.of("Apple  退货", " apple 退货 ", "Nike配送", "APPLE 退货")));
    assertThat(r.getQueries()).containsExactly("Apple  退货", "Nike配送");
  }

  @Test
  void rejectsBadNestedValuesAndMixedFilters() {
    for (var values :
        List.of(List.of(42), List.of(" "), List.of("a".repeat(501)), List.of("a", "b", "c", "d")))
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> KnowledgeRetrievalRequest.from(Map.of("query", "政策", "queries", values)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                KnowledgeRetrievalRequest.from(
                    Map.of("query", "苹果退货", "queries", List.of("苹果退货"), "brand", "APPLE")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> KnowledgeRetrievalRequest.from(Map.of("query", "苹果退货", "requestId", "fake")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> KnowledgeRetrievalRequest.from(Map.of("query", 22)));
  }
}
