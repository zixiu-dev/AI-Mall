package com.bitmall.agent.agent.service.runtime.demo;

import com.bitmall.agent.aftersales.service.AfterSalesService;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.service.memory.MemoryService;
import com.bitmall.agent.agent.service.runtime.AgentResponder;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.agent.service.tool.ToolDispatcher;
import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.coupon.service.CouponService;
import com.bitmall.agent.coupon.vo.CouponVO;
import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.knowledge.vo.KnowledgeHitVO;
import com.bitmall.agent.knowledge.vo.KnowledgeSearchResultVO;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.service.ProductService;
import com.bitmall.agent.product.vo.ProductCardVO;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import com.bitmall.agent.product.vo.SkuVO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 不需要模型密钥的本地演示助手。
 *
 * <p>用关键词规则判断用户想做什么，但查询的是真实业务服务；下单、领券、取消订单、售后等写操作仍交给 ToolDispatcher，只生成待确认卡，不会直接执行。
 *
 * <p>规则按固定顺序匹配，先命中的分支优先，调整顺序会改变识别结果。
 */
@Component
@RequiredArgsConstructor
public class DemoAgentResponder implements AgentResponder {

  /** 识别演示输入中的订单号，只认明确格式，避免从任意数字猜订单。 */
  private static final Pattern ORDER_ID = Pattern.compile("(?:BM|WW)\\d{8}[A-Z0-9]+|BM\\d{12}");

  /** 识别用户明确写出的 SKU 编号。 */
  private static final Pattern SKU_ID = Pattern.compile("(?:SKU|sku)[：: ]?(\\d+)");

  /** 识别明确的商品编号；预算、订单编号和 SKU 不属于商品详情查询。 */
  private static final Pattern PRODUCT_ID =
      Pattern.compile(
          "(?i)(?:商品\\s*(?:ID|编号)\\s*[:：#]?\\s*|(?:查看|看看|介绍|了解)(?:一下)?\\s*商品\\s*)(\\d+)");

  /** 识别用户明确指定的优惠券活动编号。 */
  private static final Pattern COUPON_ID = Pattern.compile("(?:优惠券|券)[：: #]*(\\d+)");

  /** "预算 300"、"不超过 300"、"最多 300" 这类价格上限写法。 */
  private static final Pattern BUDGET_PREFIX =
      Pattern.compile("(?:预算|不超过|最多)\\s*(\\d+(?:\\.\\d{1,2})?)");

  /** "300 元以内"、"300以下" 这类价格上限写法。 */
  private static final Pattern PRICE_CEILING_SUFFIX =
      Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*元?(?:以内|以下)");

  /** "42 码" 这类尺码写法。 */
  private static final Pattern SHOE_SIZE = Pattern.compile("(\\d{2})\\s*码");

  private static final String DIGITS_ONLY = "\\d+";

  /** 涉及政策的话题，可能只是咨询，也可能是办理请求，需要结合 TRANSACTION_REQUEST_KEYWORDS 判断。 */
  private static final String[] POLICY_TOPIC_KEYWORDS = {
    "退货", "换货", "退款", "退吗", "退么", "能退", "售后", "保修", "质保", "维修", "配送", "运费", "包邮", "发货", "政策", "规则"
  };

  /** 明确要求办理业务的说法；出现这些词时不按政策咨询处理。 */
  private static final String[] TRANSACTION_REQUEST_KEYWORDS = {
    "帮我申请", "帮我退", "申请退", "办理退", "我要退", "我想退", "要换货", "购买", "下单", "帮我买", "加入购物车", "取消订单", "领取", "领券"
  };

  /** 规则咨询的典型问法，命中时优先查知识库而不是发起交易。 */
  private static final String[] RULE_QUESTION_KEYWORDS = {
    "规则", "条件", "政策", "多久到账", "要多久", "几天", "怎么计算", "是否包邮", "保修多久"
  };

  /** 前面分支都没命中时，再用这组词兜底判断是否为知识库问题。 */
  private static final String[] KNOWLEDGE_FALLBACK_KEYWORDS = {
    "规则", "几天", "包邮", "保修", "优惠券", "退款到账"
  };

  private static final String[] CLAIM_COUPON_KEYWORDS = {"领取", "帮我领", "领这张"};

  private static final String[] LOGISTICS_KEYWORDS = {"物流", "到哪", "送到"};

  private static final String[] CANCEL_ORDER_KEYWORDS = {"取消订单", "取消这个订单"};

  private static final String[] AFTER_SALES_KEYWORDS = {"退货", "退款", "换货"};

  /** 售后话题下，出现这些词说明用户要提交申请，而不只是查资格。 */
  private static final String[] SUBMIT_AFTER_SALES_KEYWORDS = {
    "帮我", "申请", "办理", "想退", "要退", "破损", "坏了"
  };

  private static final String[] PURCHASE_KEYWORDS = {"下单", "购买", "买这个"};

  /** 演示模式能识别的商品关键词，按顺序匹配第一个；真实 AI 模式由模型给出结构化搜索参数。 */
  private static final List<String> PRODUCT_KEYWORDS =
      List.of("笔记本", "电脑", "键盘", "耳机", "手表", "咖啡机", "台灯", "背包", "跑鞋", "鞋");

  private static final List<String> COLOR_KEYWORDS = List.of("黑色", "白色", "灰色", "蓝色", "绿色");

  private static final String SKILL_COUPON_OPS = "coupon_ops";
  private static final String SKILL_ORDER_CANCEL = "order_cancel_flow";
  private static final String SKILL_AFTER_SALES = "after_sales";
  private static final String SKILL_ORDER_CREATE = "order_create_flow";

  private static final String ASSISTANT_ROLE = "assistant";
  private static final String AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";

  /** 演示模式下知识检索的超时时间（毫秒）。 */
  private static final int KNOWLEDGE_TIMEOUT_MILLIS = 8000;

  /** 演示模式下单固定购买 1 件。 */
  private static final int DEMO_ORDER_QUANTITY = 1;

  /** 商品搜索最多返回的条数。 */
  private static final int SEARCH_LIMIT = 4;

  /** "最近订单"展示的条数。 */
  private static final int RECENT_ORDER_LIMIT = 5;

  /** 演示模式下的商品搜索。 */
  private final ProductService catalog;

  /** 演示模式下查询用户可用优惠券。 */
  private final CouponService coupons;

  /** 演示模式下生成结算报价。 */
  private final CheckoutService checkout;

  /** 演示模式下查询订单列表和物流。 */
  private final OrderService orders;

  /** 演示模式下做售后申请前置检查。 */
  private final AfterSalesService afterSales;

  /** 演示模式下检索售后政策等知识。 */
  private final KnowledgeService knowledge;

  /** 写操作前需要标记对应技能已加载，否则 ToolDispatcher 会拒绝。 */
  private final SkillService skills;

  /** 用户说"记住……"时，把这句话保存为长期偏好。 */
  private final MemoryService memories;

  /** 统一的工具入口：查询直接执行，写操作转为待确认提议。 */
  private final ToolDispatcher dispatcher;

  /** 把订单、物流等业务结果格式化成缩进 JSON，直接拼进演示回复正文。 */
  private final ObjectMapper objectMapper;

  /**
   * 按关键词规则依次判断用户意图并给出回复。
   *
   * <p>判断顺序：记住偏好 -> 政策咨询 -> 优惠券 -> 物流 -> 取消订单 -> 规则问题 -> 售后 -> 下单 -> 订单列表 -> 其他知识问题 -> 商品详情 -> 商品搜索。
   *
   * <p>任何业务异常都转成"这一步没有办成"的提示，不向外抛出。
   */
  @Override
  public String respond(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages) {
    ToolExecutionContext context =
        buildContext(userId, conversationId, requestId, message, recentMessages);
    try {
      if (message.contains("记住")) {
        return rememberPreference(userId, conversationId, message);
      }
      if (isPolicyConsultation(message)) {
        return answerKnowledge(message, context);
      }
      if (message.contains("优惠券") || message.contains("领券")) {
        return handleCoupon(userId, conversationId, message, context);
      }

      String orderId = match(ORDER_ID, message, 0);
      if (containsAny(message, LOGISTICS_KEYWORDS)) {
        return handleLogistics(userId, orderId);
      }
      if (containsAny(message, CANCEL_ORDER_KEYWORDS)) {
        return handleCancelOrder(conversationId, orderId, context);
      }
      if (isRuleQuestion(message)) {
        return answerKnowledge(message, context);
      }
      if (containsAny(message, AFTER_SALES_KEYWORDS)) {
        return handleAfterSales(userId, conversationId, orderId, message, context);
      }
      if (containsAny(message, PURCHASE_KEYWORDS)) {
        return handlePurchase(userId, conversationId, message, context);
      }
      if (message.contains("订单")) {
        return "这是你最近的订单：\n" + pretty(orders.orders(userId, RECENT_ORDER_LIMIT));
      }
      if (containsAny(message, KNOWLEDGE_FALLBACK_KEYWORDS)) {
        return answerKnowledge(message, context);
      }

      // 明确写出商品编号时直接查详情；只回复一个数字时，必须能在上一条助手回复里找到对应商品。
      String productId = resolveProductId(message, recentMessages);
      if (productId != null) {
        return showProductDetail(productId, context);
      }
      if (isDigitsOnly(message)) {
        return askWhatNumberMeans(message.strip());
      }
      return searchProducts(message);
    } catch (Exception e) {
      return "这一步没有办成：" + rootMessage(e);
    }
  }

  /** 组装工具执行上下文；知识检索会参考最近几条对话。 */
  private static ToolExecutionContext buildContext(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages) {
    List<String> recentLines =
        recentMessages.stream().map(m -> m.getRole() + ":" + m.getContent()).toList();
    KnowledgeExecutionContext knowledgeContext =
        new KnowledgeExecutionContext(requestId, message, recentLines, KNOWLEDGE_TIMEOUT_MILLIS);
    return new ToolExecutionContext(userId, conversationId, requestId, knowledgeContext);
  }

  /** 用户说"记住……"时，把去掉"请记住/记住"后的内容保存为长期偏好。 */
  private String rememberPreference(long userId, String conversationId, String message) {
    String preference = message.replace("请记住", "").replace("记住", "").trim();
    memories.upsert(userId, "explicit_preference", preference, conversationId);
    return "好的，我已经记住这个购物偏好：" + preference;
  }

  /**
   * 是否为只读的政策咨询（如"这个能退吗"）。
   *
   * <p>条件：提到政策话题、没有明确的办理说法，并且是规则类问法或没有给出订单号。给了订单号且不是规则问法时，交给后面的售后分支查具体订单。
   */
  private static boolean isPolicyConsultation(String message) {
    boolean policyTopic = containsAny(message, POLICY_TOPIC_KEYWORDS);
    boolean transactionRequest = containsAny(message, TRANSACTION_REQUEST_KEYWORDS);
    boolean generalQuestion = isRuleQuestion(message) || match(ORDER_ID, message, 0) == null;
    return policyTopic && !transactionRequest && generalQuestion;
  }

  /** 明确要领取且给出了券编号时生成领券确认卡，否则展示优惠券中心。 */
  private String handleCoupon(
      long userId, String conversationId, String message, ToolExecutionContext context) {
    String couponId = match(COUPON_ID, message, 1);
    boolean wantsToClaim = containsAny(message, CLAIM_COUPON_KEYWORDS);
    if (wantsToClaim && couponId != null) {
      skills.markLoaded(conversationId, SKILL_COUPON_OPS);
      Object result =
          dispatcher.dispatch("coupon_claim", context, Map.of("couponId", Long.parseLong(couponId)));
      return pendingText(result);
    }
    return renderCoupons(coupons.coupons(userId));
  }

  private String handleLogistics(long userId, String orderId) {
    if (orderId == null) {
      return "请告诉我具体订单号，我才能查询物流。演示订单号：BM202610010001。";
    }
    Map<String, Object> logistics = orders.logistics(userId, orderId);
    return "订单 " + orderId + " 当前物流：\n" + pretty(logistics);
  }

  /** 取消订单只生成确认卡，用户确认后才真正取消。 */
  private String handleCancelOrder(
      String conversationId, String orderId, ToolExecutionContext context) {
    if (orderId == null) {
      return "请提供需要取消的订单号。";
    }
    skills.markLoaded(conversationId, SKILL_ORDER_CANCEL);
    Object result = dispatcher.dispatch("order_cancel", context, Map.of("orderId", orderId));
    return pendingText(result);
  }

  /**
   * 售后：先做资格检查；用户明确要办理时，用检查得到的可退金额生成售后确认卡，否则只展示检查结果。
   */
  private String handleAfterSales(
      long userId,
      String conversationId,
      String orderId,
      String message,
      ToolExecutionContext context) {
    if (orderId == null) {
      return "请提供需要办理售后的订单号，例如 BM202610010001，并说明退款、退货还是换货。";
    }
    skills.markLoaded(conversationId, SKILL_AFTER_SALES);
    String type = afterSalesType(message);
    Map<String, Object> check = afterSales.afterSalesPrecheck(userId, orderId, type);

    boolean wantsToSubmit = containsAny(message, SUBMIT_AFTER_SALES_KEYWORDS);
    if (!wantsToSubmit) {
      return "我先完成了售后资格检查：\n" + pretty(check) + "\n如果要提交，请补充具体原因，我会生成确认卡。";
    }
    Object result =
        dispatcher.dispatch(
            "after_sales_submit",
            context,
            Map.of(
                "orderId", orderId,
                "type", type,
                "reason", message,
                "expectedRefundAmount", check.get("refundableAmount")));
    return "售后资格已核验。" + pendingText(result);
  }

  /** 按用户说法判断售后类型：提到换货优先算换货，其次退货，其余都按仅退款。 */
  private static String afterSalesType(String message) {
    if (message.contains("换货")) {
      return "EXCHANGE";
    }
    if (message.contains("退货")) {
      return "RETURN";
    }
    return "REFUND";
  }

  /** 下单：先按 SKU 核算价格和默认地址，再用核算出的应付金额生成下单确认卡。 */
  private String handlePurchase(
      long userId, String conversationId, String message, ToolExecutionContext context) {
    String skuText = match(SKU_ID, message, 1);
    if (skuText == null) {
      return "请告诉我具体 SKU 编号和数量。你可以在商品卡片里点击“让助手下单”。";
    }
    long skuId = Long.parseLong(skuText);
    CheckoutQuoteVO quote = checkout.checkoutQuote(userId, skuId, DEMO_ORDER_QUANTITY, null);
    skills.markLoaded(conversationId, SKILL_ORDER_CREATE);
    Object result =
        dispatcher.dispatch(
            "order_create",
            context,
            Map.of(
                "skuId", skuId,
                "quantity", DEMO_ORDER_QUANTITY,
                "addressId", quote.getAddress().getId(),
                "expectedPayAmount", quote.getPayAmount()));
    return "结算信息已核验。" + pendingText(result);
  }

  private String showProductDetail(String productId, ToolExecutionContext context) {
    ProductDetailVO product =
        (ProductDetailVO)
            dispatcher.dispatch(
                "product_detail", context, Map.of("productId", Long.parseLong(productId)));
    return renderProduct(product);
  }

  /** 用户只发了一个数字但无法对应商品时，请用户说明是商品 ID 还是预算，不擅自猜测。 */
  private static String askWhatNumberMeans(String number) {
    return "请说明这个数字的含义。如果想查看某件商品，请说“商品ID "
        + number
        + "”；如果是预算，请说“预算 "
        + number
        + " 元以内”。";
  }

  /** 从文字中提取商品关键词、价格上限、尺码和颜色，只搜索有库存的商品。条件不放宽，找不到就如实说明。 */
  private String searchProducts(String message) {
    Map<String, Object> searchArgs = buildSearchArgs(message);
    ProductSearchResultVO.Page page = catalog.searchCatalog(ProductSearchRequest.from(searchArgs));
    List<ProductCardVO> products =
        page.getItems().stream().map(ProductSearchResultVO.Hit::card).toList();
    if (products.isEmpty()) {
      return "当前商品条件下没有找到可售商品。可以调整关键词、预算或规格后再查，我不会自动放宽你的条件。";
    }
    StringBuilder answer = new StringBuilder("我从当前商品和库存中选了这些：\n");
    for (ProductCardVO product : products) {
      answer
          .append("- ")
          .append(product.getName())
          .append("，¥")
          .append(product.getPrice())
          .append("，库存 ")
          .append(product.getStock())
          .append("，商品ID ")
          .append(product.getId())
          .append('\n');
    }
    answer.append("想看哪一款的具体规格，可以告诉我商品 ID。");
    return answer.toString();
  }

  /** 组装搜索参数；"预算/不超过/最多 N" 的写法优先于 "N 元以内" 的写法，颜色取第一个命中的。 */
  private static Map<String, Object> buildSearchArgs(String message) {
    Map<String, Object> searchArgs = new LinkedHashMap<>();
    searchArgs.put("query", extractProductQuery(message));
    searchArgs.put("inStock", true);
    searchArgs.put("limit", SEARCH_LIMIT);

    String maxPrice = match(BUDGET_PREFIX, message, 1);
    if (maxPrice == null) {
      maxPrice = match(PRICE_CEILING_SUFFIX, message, 1);
    }
    if (maxPrice != null) {
      searchArgs.put("maxPrice", new BigDecimal(maxPrice));
    }

    String size = match(SHOE_SIZE, message, 1);
    if (size != null) {
      searchArgs.put("size", size);
    }

    for (String color : COLOR_KEYWORDS) {
      if (message.contains(color)) {
        searchArgs.put("color", color);
        break;
      }
    }
    return searchArgs;
  }

  /**
   * 解析用户想看的商品编号。
   *
   * <p>优先使用"商品ID 12"这类明确写法。用户只回复一个数字时，只在最近一条助手回复里列出过这个商品编号才认，不从更早的话题或其他会话猜测。
   */
  private String resolveProductId(String message, List<ChatMessageDO> recentMessages) {
    String explicitId = match(PRODUCT_ID, message, 1);
    if (explicitId != null) {
      return explicitId;
    }
    String number = message.strip();
    if (!number.matches(DIGITS_ONLY)) {
      return null;
    }
    ChatMessageDO lastAssistantMessage = findLastAssistantMessage(recentMessages);
    if (lastAssistantMessage == null) {
      return null;
    }
    Matcher offeredIds = PRODUCT_ID.matcher(lastAssistantMessage.getContent());
    while (offeredIds.find()) {
      if (number.equals(offeredIds.group(1))) {
        return number;
      }
    }
    return null;
  }

  /** 从后往前找最近一条助手消息，找不到返回 null。 */
  private static ChatMessageDO findLastAssistantMessage(List<ChatMessageDO> recentMessages) {
    for (int i = recentMessages.size() - 1; i >= 0; i--) {
      ChatMessageDO previous = recentMessages.get(i);
      if (ASSISTANT_ROLE.equals(previous.getRole())) {
        return previous;
      }
    }
    return null;
  }

  /** 展示商品详情和各规格的 SKU 编号，帮助用户区分商品 ID 与购买需要的 SKU；不会自动下单。 */
  private String renderProduct(ProductDetailVO product) {
    StringBuilder answer =
        new StringBuilder("### ")
            .append(product.getName())
            .append("（商品ID ")
            .append(product.getId())
            .append("）\n\n")
            .append(product.getSummary())
            .append("\n\n| SKU 编号 | 规格 | 单价 | 库存 |\n")
            .append("|---|---|---:|---:|\n");
    for (SkuVO sku : product.getSkus()) {
      String escapedSpec = sku.getSpec().replace("|", "\\|");
      answer
          .append("| ")
          .append(sku.getId())
          .append(" | ")
          .append(escapedSpec)
          .append(" | ¥")
          .append(sku.getPrice().toPlainString())
          .append(" | ")
          .append(sku.getStock())
          .append(" |\n");
    }
    answer.append("\n商品 ID 用于查看整款商品，SKU 编号用于选择购买规格。");

    SkuVO firstInStock = findFirstInStockSku(product.getSkus());
    if (firstInStock != null) {
      answer.append("要购买可告诉我“购买 SKU ").append(firstInStock.getId()).append("”；我会先核算价格，再请你确认。");
    } else {
      answer.append("当前规格暂时无货。");
    }
    return answer.toString();
  }

  private static SkuVO findFirstInStockSku(List<SkuVO> skus) {
    for (SkuVO sku : skus) {
      if (sku.getStock() > 0) {
        return sku;
      }
    }
    return null;
  }

  /** 工具返回待确认结果时，引导用户核对确认卡；其他结果直接格式化展示。 */
  private String pendingText(Object result) {
    if (result instanceof Map<?, ?> map && AWAITING_CONFIRMATION.equals(map.get("status"))) {
      return "\n请核对下方确认卡：" + map.get("summary");
    }
    return pretty(result);
  }

  /** 把优惠券列表整理成 Markdown 表格。 */
  private String renderCoupons(List<CouponVO> coupons) {
    StringBuilder answer =
        new StringBuilder("这是当前优惠券中心：\n\n")
            .append("| 编号 | 优惠券 | 使用门槛 | 优惠金额 | 状态 |\n")
            .append("|---|---|---:|---:|---|\n");
    for (CouponVO coupon : coupons) {
      answer
          .append("| ")
          .append(coupon.getId())
          .append(" | ")
          .append(coupon.getName())
          .append(" | ")
          .append(thresholdLabel(coupon))
          .append(" | ¥")
          .append(coupon.getDiscountAmount().stripTrailingZeros().toPlainString())
          .append(" | ")
          .append(couponStatusLabel(coupon))
          .append(" |\n");
    }
    return answer.append("\n需要领取时请告诉我优惠券编号。").toString();
  }

  private static String thresholdLabel(CouponVO coupon) {
    if (coupon.getThresholdAmount().signum() == 0) {
      return "无门槛";
    }
    return "¥" + coupon.getThresholdAmount().stripTrailingZeros().toPlainString();
  }

  /** 用户已用过显示"已使用"，已领未用显示"已领取"；用户没领过时，按剩余库存显示"可领取"或"已领完"。 */
  private static String couponStatusLabel(CouponVO coupon) {
    if ("USED".equals(coupon.getUserStatus())) {
      return "已使用";
    }
    if ("AVAILABLE".equals(coupon.getUserStatus())) {
      return "已领取";
    }
    if (coupon.getRemainingStock() > 0) {
      return "可领取";
    }
    return "已领完";
  }

  /** 查询知识库并展示命中的政策原文、适用范围和来源；没有命中时直接返回检索给出的说明，不跨品牌猜测政策。 */
  private String answerKnowledge(String message, ToolExecutionContext context) {
    KnowledgeSearchResultVO result =
        knowledge.retrieve(
            new KnowledgeRetrievalRequest(message, null, null, null, null),
            context.getKnowledgeContext());
    if (result.getHits().isEmpty()) {
      return result.getMessage();
    }
    StringBuilder answer = new StringBuilder(result.getMessage());
    for (KnowledgeHitVO hit : result.getHits()) {
      answer.append("\n\n**").append(hit.getTitle()).append("**\n").append(hit.getContent());
      answer
          .append("\n适用渠道：")
          .append(hit.getApplicableChannel())
          .append("；地区：")
          .append(regionLabel(hit.getRegion()))
          .append("；核对日期：")
          .append(hit.getSourceCheckedAt())
          .append("。");
      appendSource(answer, hit);
    }
    return answer.toString();
  }

  private static String regionLabel(String region) {
    if ("CN_MAINLAND".equals(region)) {
      return "中国大陆";
    }
    return region;
  }

  /** 只有 https 链接才渲染成可点击的 Markdown 链接，其他情况只显示来源名称。 */
  private static void appendSource(StringBuilder answer, KnowledgeHitVO hit) {
    boolean hasSafeLink = hit.getSourceUrl() != null && hit.getSourceUrl().startsWith("https://");
    if (hasSafeLink) {
      answer
          .append("\n来源：[")
          .append(hit.getSourceName())
          .append("](")
          .append(hit.getSourceUrl())
          .append(")");
    } else {
      answer.append("\n来源：").append(hit.getSourceName());
    }
  }

  /** 返回第一个出现在文字中的商品关键词，都没有时返回空字符串（搜索全部商品）。 */
  private static String extractProductQuery(String message) {
    for (String keyword : PRODUCT_KEYWORDS) {
      if (message.contains(keyword)) {
        return keyword;
      }
    }
    return "";
  }

  /** 把业务结果格式化为缩进 JSON，便于阅读；格式化失败时退回 toString。 */
  private String pretty(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  private static boolean isDigitsOnly(String message) {
    return message.strip().matches(DIGITS_ONLY);
  }

  /** 判断文字是否包含任意一个候选关键词。 */
  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  /** 是否为规则类问法（如"要多久""是否包邮"），这类问题优先查知识库而不是发起交易。 */
  private static boolean isRuleQuestion(String message) {
    return containsAny(message, RULE_QUESTION_KEYWORDS);
  }

  /** 用正则从文字中提取第一个匹配的指定分组，没有匹配时返回 null。 */
  private static String match(Pattern pattern, String value, int group) {
    Matcher matcher = pattern.matcher(value);
    if (matcher.find()) {
      return matcher.group(group);
    }
    return null;
  }

  /** 沿异常原因链找到最内层错误信息，用来告诉用户这一步为什么没办成。 */
  private static String rootMessage(Exception exception) {
    Throwable root = exception;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    if (root.getMessage() == null) {
      return "未知错误";
    }
    return root.getMessage();
  }
}
