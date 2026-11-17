package com.bitmall.agent.agent.service.runtime.demo;

import com.bitmall.agent.aftersales.service.AfterSalesService;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.service.memory.MemoryService;
import com.bitmall.agent.agent.service.runtime.AgentResponder;
import com.bitmall.agent.agent.service.skill.SkillService;
import com.bitmall.agent.agent.service.tool.ToolDispatcher;
import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.coupon.service.CouponService;
import com.bitmall.agent.coupon.vo.CouponVO;
import com.bitmall.agent.knowledge.dto.KnowledgeRetrievalRequest;
import com.bitmall.agent.knowledge.model.KnowledgeExecutionContext;
import com.bitmall.agent.knowledge.service.KnowledgeService;
import com.bitmall.agent.order.service.OrderService;
import com.bitmall.agent.product.service.ProductService;
import com.bitmall.agent.product.vo.ProductCardVO;
import com.bitmall.agent.product.vo.ProductDetailVO;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 无需模型密钥的本地演示实现。 用关键词规则识别请求，但仍访问真实业务服务，写操作仍通过 ToolDispatcher 生成待确认卡。 */
@Component
public class DemoAgentResponder implements AgentResponder {

  /** 识别演示输入中的订单号，避免从任意数字猜订单。 */
  private static final Pattern ORDER_ID = Pattern.compile("(?:BM|WW)\\d{8}[A-Z0-9]+|BM\\d{12}");

  /** 识别用户明确写出的 SKU 编号。 */
  private static final Pattern SKU_ID = Pattern.compile("(?:SKU|sku)[：: ]?(\\d+)");

  /** 识别明确的商品编号；预算、订单编号和 SKU 不属于商品详情查询。 */
  private static final Pattern PRODUCT_ID =
      Pattern.compile(
          "(?i)(?:商品\\s*(?:ID|编号)\\s*[:：#]?\\s*|(?:查看|看看|介绍|了解)(?:一下)?\\s*商品\\s*)(\\d+)");

  /** 识别用户明确指定的优惠券活动编号。 */
  private static final Pattern COUPON_ID = Pattern.compile("(?:优惠券|券)[：: #]*(\\d+)");

  /** 依赖组件：商品展示查询的业务入口。 */
  private final ProductService catalog;

  /** 依赖组件：优惠券查询、预览和领取的业务服务。 */
  private final CouponService coupons;

  /** 依赖组件：统一计算立即购买和购物车的结算报价。 */
  private final CheckoutService checkout;

  /** 依赖组件：订单交易业务服务。 */
  private final OrderService orders;

  /** 依赖组件：售后业务服务。 */
  private final AfterSalesService afterSales;

  /** 依赖组件：规则知识检索的业务入口。 */
  private final KnowledgeService knowledge;

  /** 依赖组件：业务技能目录和会话加载状态的入口。 */
  private final SkillService skills;

  /** 依赖组件：读取和保存用户明确表达的长期购物偏好。 */
  private final MemoryService memories;

  /** 依赖组件：工具调用的统一分发入口。 */
  private final ToolDispatcher dispatcher;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper objectMapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public DemoAgentResponder(
      ProductService catalog,
      CouponService coupons,
      CheckoutService checkout,
      OrderService orders,
      AfterSalesService afterSales,
      KnowledgeService knowledge,
      SkillService skills,
      MemoryService memories,
      ToolDispatcher dispatcher,
      ObjectMapper objectMapper) {
    this.catalog = catalog;
    this.coupons = coupons;
    this.checkout = checkout;
    this.orders = orders;
    this.afterSales = afterSales;
    this.knowledge = knowledge;
    this.skills = skills;
    this.memories = memories;
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
  }

  /** 按关键词识别演示请求，调用真实查询服务；涉及下单、领券或售后时仍交给工具分发生成确认卡。 */
  @Override
  public String respond(
      long userId,
      String conversationId,
      String requestId,
      String message,
      List<ChatMessageDO> recentMessages) {
    ToolExecutionContext context =
        new ToolExecutionContext(
            userId,
            conversationId,
            requestId,
            new KnowledgeExecutionContext(
                requestId,
                message,
                recentMessages.stream().map(m -> m.getRole() + ":" + m.getContent()).toList(),
                8000));
    try {
      if (message.contains("记住")) {
        String value = message.replace("请记住", "").replace("记住", "").trim();
        memories.upsert(userId, "explicit_preference", value, conversationId);
        return "好的，我已经记住这个购物偏好：" + value;
      }
      // 品牌政策咨询只读检索；明确“帮我申请”等请求仍走原售后确认链路。
      boolean policyTopic =
          containsAny(
              message, "退货", "换货", "退款", "退吗", "退么", "能退", "售后", "保修", "质保", "维修", "配送", "运费", "包邮",
              "发货", "政策", "规则");
      boolean afterSalesWrite =
          containsAny(
              message, "帮我申请", "帮我退", "申请退", "办理退", "我要退", "我想退", "要换货", "购买", "下单", "帮我买", "加入购物车",
              "取消订单", "领取", "领券");
      if (policyTopic && !afterSalesWrite && (isRuleQuestion(message) || orderIdAbsent(message)))
        return answerKnowledge(message, context);
      if (message.contains("优惠券") || message.contains("领券")) {
        String couponText = match(COUPON_ID, message, 1);
        if (containsAny(message, "领取", "帮我领", "领这张") && couponText != null) {
          skills.markLoaded(conversationId, "coupon_ops");
          Object result =
              dispatcher.dispatch(
                  "coupon_claim", context, Map.of("couponId", Long.parseLong(couponText)));
          return pendingText(result);
        }
        return renderCoupons(coupons.coupons(userId));
      }
      String orderId = match(ORDER_ID, message, 0);
      if (message.contains("物流") || message.contains("到哪") || message.contains("送到")) {
        if (orderId == null) return "请告诉我具体订单号，我才能查询物流。演示订单号：BM202610010001。";
        var result = orders.logistics(userId, orderId);
        return "订单 " + orderId + " 当前物流：\n" + pretty(result);
      }
      if (message.contains("取消订单") || message.contains("取消这个订单")) {
        if (orderId == null) return "请提供需要取消的订单号。";
        skills.markLoaded(conversationId, "order_cancel_flow");
        Object result = dispatcher.dispatch("order_cancel", context, Map.of("orderId", orderId));
        return pendingText(result);
      }
      if (isRuleQuestion(message)) {
        return answerKnowledge(message, context);
      }
      if (message.contains("退货") || message.contains("退款") || message.contains("换货")) {
        if (orderId == null) return "请提供需要办理售后的订单号，例如 BM202610010001，并说明退款、退货还是换货。";
        skills.markLoaded(conversationId, "after_sales");
        String type =
            message.contains("换货") ? "EXCHANGE" : message.contains("退货") ? "RETURN" : "REFUND";
        Map<String, Object> check = afterSales.afterSalesPrecheck(userId, orderId, type);
        if (containsAny(message, "帮我", "申请", "办理", "想退", "要退", "破损", "坏了")) {
          Object result =
              dispatcher.dispatch(
                  "after_sales_submit",
                  context,
                  Map.of(
                      "orderId",
                      orderId,
                      "type",
                      type,
                      "reason",
                      message,
                      "expectedRefundAmount",
                      check.get("refundableAmount")));
          return "售后资格已核验。" + pendingText(result);
        }
        return "我先完成了售后资格检查：\n" + pretty(check) + "\n如果要提交，请补充具体原因，我会生成确认卡。";
      }
      if (message.contains("下单") || message.contains("购买") || message.contains("买这个")) {
        String skuText = match(SKU_ID, message, 1);
        if (skuText == null) return "请告诉我具体 SKU 编号和数量。你可以在商品卡片里点击“让助手下单”。";
        long skuId = Long.parseLong(skuText);
        var quote = checkout.checkoutQuote(userId, skuId, 1, null);
        skills.markLoaded(conversationId, "order_create_flow");
        Object result =
            dispatcher.dispatch(
                "order_create",
                context,
                Map.of(
                    "skuId",
                    skuId,
                    "quantity",
                    1,
                    "addressId",
                    quote.getAddress().getId(),
                    "expectedPayAmount",
                    quote.getPayAmount()));
        return "结算信息已核验。" + pendingText(result);
      }
      if (message.contains("订单")) {
        return "这是你最近的订单：\n" + pretty(orders.orders(userId, 5));
      }
      if (containsAny(message, "规则", "几天", "包邮", "保修", "优惠券", "退款到账")) {
        return answerKnowledge(message, context);
      }

      // 明确编号直接查详情；只回复数字时，必须能在上一条助手回复里找到对应商品。
      String productId = resolveProductId(message, recentMessages);
      if (productId != null) {
        ProductDetailVO product =
            (ProductDetailVO)
                dispatcher.dispatch(
                    "product_detail", context, Map.of("productId", Long.parseLong(productId)));
        return renderProduct(product);
      }
      if (message.strip().matches("\\d+")) {
        return "请说明这个数字的含义。如果想查看某件商品，请说“商品ID "
            + message.strip()
            + "”；如果是预算，请说“预算 "
            + message.strip()
            + " 元以内”。";
      }
      Map<String, Object> searchArgs = new java.util.LinkedHashMap<>();
      searchArgs.put("query", extractProductQuery(message));
      searchArgs.put("inStock", true);
      searchArgs.put("limit", 4);
      Matcher budget = Pattern.compile("(?:预算|不超过|最多)\\s*(\\d+(?:\\.\\d{1,2})?)").matcher(message);
      Matcher ceiling = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*元?(?:以内|以下)").matcher(message);
      if (budget.find()) searchArgs.put("maxPrice", new java.math.BigDecimal(budget.group(1)));
      else if (ceiling.find())
        searchArgs.put("maxPrice", new java.math.BigDecimal(ceiling.group(1)));
      Matcher size = Pattern.compile("(\\d{2})\\s*码").matcher(message);
      if (size.find()) searchArgs.put("size", size.group(1));
      for (String color : List.of("黑色", "白色", "灰色", "蓝色", "绿色"))
        if (message.contains(color)) {
          searchArgs.put("color", color);
          break;
        }
      var search =
          catalog.searchCatalog(
              com.bitmall.agent.product.dto.ProductSearchRequest.from(searchArgs));
      List<ProductCardVO> products =
          search.getItems().stream()
              .map(com.bitmall.agent.product.vo.ProductSearchResultVO.Hit::card)
              .toList();
      if (!products.isEmpty()) {
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
      return "当前商品条件下没有找到可售商品。可以调整关键词、预算或规格后再查，我不会自动放宽你的条件。";
    } catch (Exception e) {
      return "这一步没有办成：" + rootMessage(e);
    }
  }

  /** 纯数字只承接最近一条助手回复里的商品编号，不从更早的话题或其他会话猜测。 */
  private String resolveProductId(String message, List<ChatMessageDO> recentMessages) {
    String explicit = match(PRODUCT_ID, message, 1);
    if (explicit != null) return explicit;
    String number = message.strip();
    if (!number.matches("\\d+")) return null;
    for (int i = recentMessages.size() - 1; i >= 0; i--) {
      ChatMessageDO previous = recentMessages.get(i);
      if (!"assistant".equals(previous.getRole())) continue;
      Matcher offered = PRODUCT_ID.matcher(previous.getContent());
      while (offered.find()) {
        if (number.equals(offered.group(1))) return number;
      }
      break;
    }
    return null;
  }

  /** 展示真实详情和规格编号，帮助用户区分商品 ID 与购买时需要的 SKU，不自动创建订单。 */
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
    for (var sku : product.getSkus()) {
      answer
          .append("| ")
          .append(sku.getId())
          .append(" | ")
          .append(sku.getSpec().replace("|", "\\|"))
          .append(" | ¥")
          .append(sku.getPrice().toPlainString())
          .append(" | ")
          .append(sku.getStock())
          .append(" |\n");
    }
    answer.append("\n商品 ID 用于查看整款商品，SKU 编号用于选择购买规格。");
    product.getSkus().stream()
        .filter(sku -> sku.getStock() > 0)
        .findFirst()
        .ifPresentOrElse(
            sku -> answer.append("要购买可告诉我“购买 SKU ").append(sku.getId()).append("”；我会先核算价格，再请你确认。"),
            () -> answer.append("当前规格暂时无货。"));
    return answer.toString();
  }

  /** 把工具的待确认结果改写成可阅读提示，其他结果直接格式化。 */
  private String pendingText(Object result) {
    if (result instanceof Map<?, ?> map && "AWAITING_CONFIRMATION".equals(map.get("status"))) {
      return "\n请核对下方确认卡：" + map.get("summary");
    }
    return pretty(result);
  }

  /** 把真实优惠券列表整理成 Markdown 表格，区分可领取、已领取、已使用和已领完。 */
  private String renderCoupons(List<CouponVO> coupons) {
    StringBuilder answer =
        new StringBuilder("这是当前优惠券中心：\n\n")
            .append("| 编号 | 优惠券 | 使用门槛 | 优惠金额 | 状态 |\n")
            .append("|---|---|---:|---:|---|\n");
    for (CouponVO coupon : coupons) {
      String threshold =
          coupon.getThresholdAmount().signum() == 0
              ? "无门槛"
              : "¥" + coupon.getThresholdAmount().stripTrailingZeros().toPlainString();
      String status =
          "USED".equals(coupon.getUserStatus())
              ? "已使用"
              : "AVAILABLE".equals(coupon.getUserStatus())
                  ? "已领取"
                  : coupon.getRemainingStock() > 0 ? "可领取" : "已领完";
      answer
          .append("| ")
          .append(coupon.getId())
          .append(" | ")
          .append(coupon.getName())
          .append(" | ")
          .append(threshold)
          .append(" | ¥")
          .append(coupon.getDiscountAmount().stripTrailingZeros().toPlainString())
          .append(" | ")
          .append(status)
          .append(" |\n");
    }
    return answer.append("\n需要领取时请告诉我优惠券编号。").toString();
  }

  /** 直接展示检索依据和来源；演示路径同样不能跨品牌猜政策。 */
  private String answerKnowledge(String message, ToolExecutionContext context) {
    var result =
        knowledge.retrieve(
            new KnowledgeRetrievalRequest(message, null, null, null, null),
            context.getKnowledgeContext());
    if (result.getHits().isEmpty()) return result.getMessage();
    StringBuilder answer = new StringBuilder(result.getMessage());
    for (var hit : result.getHits()) {
      answer.append("\n\n**").append(hit.getTitle()).append("**\n").append(hit.getContent());
      answer
          .append("\n适用渠道：")
          .append(hit.getApplicableChannel())
          .append("；地区：")
          .append("CN_MAINLAND".equals(hit.getRegion()) ? "中国大陆" : hit.getRegion())
          .append("；核对日期：")
          .append(hit.getSourceCheckedAt())
          .append("。");
      if (hit.getSourceUrl() != null && hit.getSourceUrl().startsWith("https://"))
        answer
            .append("\n来源：[")
            .append(hit.getSourceName())
            .append("](")
            .append(hit.getSourceUrl())
            .append(")");
      else answer.append("\n来源：").append(hit.getSourceName());
    }
    return answer.toString();
  }

  private static boolean orderIdAbsent(String message) {
    return match(ORDER_ID, message, 0) == null;
  }

  /** 从演示输入识别有限商品关键词；真实 AI 模式由模型提供结构化搜索参数。 */
  private String extractProductQuery(String message) {
    for (String keyword : List.of("笔记本", "电脑", "键盘", "耳机", "手表", "咖啡机", "台灯", "背包", "跑鞋", "鞋")) {
      if (message.contains(keyword)) return keyword;
    }
    return "";
  }

  /** 把业务结果格式化为缩进 JSON，便于演示回复中阅读。 */
  private String pretty(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  /** 判断文字是否包含任意一个候选关键词。 */
  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) if (value.contains(candidate)) return true;
    return false;
  }

  /** 用有限关键词区分规则咨询，优先查知识库而不是发起交易。 */
  private static boolean isRuleQuestion(String message) {
    return containsAny(message, "规则", "条件", "政策", "多久到账", "要多久", "几天", "怎么计算", "是否包邮", "保修多久");
  }

  /** 从用户文字提取正则匹配出的订单号、SKU 或优惠券编号。 */
  private static String match(Pattern pattern, String value, int group) {
    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? matcher.group(group) : null;
  }

  /** 提取业务失败的实际原因，供演示回复说明本步为何没完成。 */
  private static String rootMessage(Exception exception) {
    Throwable root = exception;
    while (root.getCause() != null) root = root.getCause();
    return root.getMessage() == null ? "未知错误" : root.getMessage();
  }
}
