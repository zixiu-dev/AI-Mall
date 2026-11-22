package com.bitmall.agent.knowledge.vo;

import java.util.List;
import lombok.Getter;

/** 品牌政策检索的规范化意图。品牌与文档类别用于硬过滤，不是模糊相关性分数。 */
@Getter
public final class KnowledgeIntentVO {
  /** 要检索的规范品牌，可包含多个；PLATFORM 仅表示本站规则。 */
  private final List<String> brands;

  /** 退换货、保修、配送、订单或优惠类别代码。 */
  private final List<String> categories;

  /** BRAND 为品牌参考，PLATFORM 为本站办理规则。 */
  private final String scope;

  private final boolean clarificationRequired;

  /** 冲突或缺失时直接返回给调用方的补问依据。 */
  private final String message;

  public KnowledgeIntentVO(
      List<String> brands,
      List<String> categories,
      String scope,
      boolean clarificationRequired,
      String message) {
    this.brands = List.copyOf(brands);
    this.categories = List.copyOf(categories);
    this.scope = scope;
    this.clarificationRequired = clarificationRequired;
    this.message = message;
  }
}
