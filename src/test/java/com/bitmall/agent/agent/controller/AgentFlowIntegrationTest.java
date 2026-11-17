package com.bitmall.agent.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 从 HTTP 入口验证商城与演示助手的完整流程。 覆盖搜索、确认后执行、防重复确认、SSE 和领券结算取消订单。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AgentFlowIntegrationTest {

  /** 本机模拟 HTTP 请求工具，在测试中验证真实 Controller 和服务调用链。 */
  @Autowired MockMvc mockMvc;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  @Autowired ObjectMapper objectMapper;

  @Test
  void statusExplicitlyIdentifiesRuleBasedDemo() throws Exception {
    mockMvc
        .perform(get("/api/agent/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("demo"));
  }

  /** 通过页面接口搜索商品，验证结果来自真实种子数据。 */
  @Test
  void productSearchUsesSeededBusinessData() throws Exception {
    mockMvc
        .perform(get("/api/store/products").param("query", "电脑"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("NovaBook Pro 14"))
        .andExpect(jsonPath("$.items[0].stock").value(greaterThan(0)));
  }

  /** 推荐后回复商品编号，应查对应商品详情和规格，而不是退回无关键词推荐。 */
  @Test
  void demoNumericReplySelectsThePreviouslyRecommendedProduct() throws Exception {
    JsonNode recommendation = demoChat(null, "我想买个电脑");
    assertThat(recommendation.path("content").asText()).contains("商品ID 101");
    JsonNode detail = demoChat(recommendation.path("conversationId").asText(), "101");
    assertThat(detail.path("content").asText())
        .contains("NovaBook Pro 14", "SKU 编号", "1001", "16GB / 512GB", "1002")
        .doesNotContain("我从当前商品和库存中选了这些");
    assertThat(detail.hasNonNull("pendingAction")).isFalse();
  }

  /** 显式商品编号和商品卡片生成的提问，无需推荐上下文也能定位详情。 */
  @Test
  void demoExplicitProductIdsOpenDetailsWithoutPriorRecommendations() throws Exception {
    for (String message :
        java.util.List.of(
            "商品ID 101",
            "商品 id：101",
            "商品编号 101",
            "查看商品 101 的详情",
            "请介绍一下商品 NovaBook Pro 14（商品ID 101），并帮我判断是否适合我。")) {
      assertThat(demoChat(null, message).path("content").asText())
          .contains("NovaBook Pro 14", "SKU 编号", "1001")
          .doesNotContain("我从当前商品和库存中选了这些");
    }
  }

  /** 新会话的纯数字不能继承其他会话的商品选择，应先明确含义。 */
  @Test
  void demoNumberWithoutProductContextAsksForClarification() throws Exception {
    demoChat(null, "我想买个电脑");
    assertThat(demoChat(null, "101").path("content").asText())
        .contains("请说明这个数字的含义")
        .doesNotContain("NovaBook Pro 14", "我从当前商品和库存中选了这些");
  }

  /** 不存在的明确编号应返回查询失败，不能悄悄用其他商品替代。 */
  @Test
  void demoUnknownProductDoesNotFallBackToRecommendations() throws Exception {
    assertThat(demoChat(null, "商品ID 999999").path("content").asText())
        .contains("商品不存在或已经下架")
        .doesNotContain("我从当前商品和库存中选了这些");
  }

  /** 带预算含义的数字继续走搜索筛选，不能误当作上一轮商品编号。 */
  @Test
  void demoBudgetFollowUpRemainsASearchRequest() throws Exception {
    JsonNode recommendation = demoChat(null, "我想买个电脑");
    assertThat(
            demoChat(recommendation.path("conversationId").asText(), "预算3000元以内的电脑")
                .path("content")
                .asText())
        .contains("NovaBook Study 14")
        .doesNotContain("NovaBook Pro 14", "SKU 编号");
  }

  /** 页面实际使用 SSE；流式入口同样需要承接上一轮推荐后的数字选择。 */
  @Test
  void demoStreamingNumericReplyAlsoReturnsProductDetails() throws Exception {
    JsonNode recommendation = demoChat(null, "我想买个电脑");
    MvcResult streaming =
        mockMvc
            .perform(
                post("/api/agent/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of(
                                "userId",
                                10001,
                                "conversationId",
                                recommendation.path("conversationId").asText(),
                                "message",
                                "101"))))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc
        .perform(asyncDispatch(streaming))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("NovaBook Pro 14")))
        .andExpect(content().string(containsString("1001")))
        .andExpect(content().string(containsString("event:done")));
  }

  /** 通过真实聊天 Controller 发起一轮演示对话，保留同会话上下文供后续请求验证。 */
  private JsonNode demoChat(String conversationId, String message) throws Exception {
    var body = objectMapper.createObjectNode().put("userId", 10001).put("message", message);
    if (conversationId != null) body.put("conversationId", conversationId);
    return objectMapper.readTree(
        mockMvc
            .perform(
                post("/api/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /** 从聊天生成订单确认卡，确认后才建单，再次确认必须被拒绝。 */
  @Test
  void writeToolWaitsForConfirmationAndCannotExecuteTwice() throws Exception {
    String chatBody =
        mockMvc
            .perform(
                post("/api/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"userId":10001,"message":"请帮我购买 SKU 1001，数量 1"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingAction.toolName").value("order_create"))
            .andExpect(jsonPath("$.loadedSkills[0]").value("order_create_flow"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode chat = objectMapper.readTree(chatBody);
    String actionId = chat.at("/pendingAction/id").asText();

    mockMvc
        .perform(
            post("/api/agent/actions/{id}/confirm", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":10001,"approved":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("订单已经创建成功")));

    mockMvc
        .perform(
            post("/api/agent/actions/{id}/confirm", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":10001,"approved":true}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("该操作已经处理或不存在"));

    String orders =
        mockMvc
            .perform(get("/api/store/orders").param("userId", "10001"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long createdOrders =
        objectMapper
            .readTree(orders)
            .path("items")
            .valueStream()
            .filter(order -> "PENDING_PAYMENT".equals(order.path("status").asText()))
            .filter(order -> order.path("items").get(0).path("skuId").asLong() == 1001L)
            .count();
    assertThat(createdOrders).isEqualTo(1);
  }

  /** 提出退货规则问题，验证回复使用知识库文档。 */
  @Test
  void ruleQuestionUsesSimplifiedKnowledgeRetrieval() throws Exception {
    mockMvc
        .perform(
            post("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":10001,"message":"退货需要满足什么条件？"}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content").value(org.hamcrest.Matchers.containsString("沃沃智购体验商城售后办理")));
  }

  /** 从 HTTP 发起流式聊天，验证开始、增量和完成事件均能到达页面。 */
  @Test
  void chatStreamUsesNamedSseEvents() throws Exception {
    MvcResult streaming =
        mockMvc
            .perform(
                post("/api/agent/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(
                        """
                        {"userId":10001,"message":"推荐一台适合编程的电脑"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(streaming))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(containsString("event:start")))
        .andExpect(content().string(containsString("event:delta")))
        .andExpect(content().string(containsString("event:done")));
  }

  /** 通过页面接口依次领券、试算、建单和取消，核对优惠金额及券状态。 */
  @Test
  void manualCouponCheckoutAndCancellationFormACompleteCommerceLoop() throws Exception {
    String claimBody =
        mockMvc
            .perform(
                post("/api/store/coupons/3002/claim")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"userId":10001}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("品质生活 · 满 500 减 80"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long userCouponId = objectMapper.readTree(claimBody).path("userCouponId").asLong();

    String quoteBody =
        mockMvc
            .perform(
                post("/api/store/checkout/quote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of(
                                "userId", 10001,
                                "source", "DIRECT",
                                "skuId", 1005,
                                "quantity", 1,
                                "userCouponId", userCouponId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goodsAmount").value(899.00))
            .andExpect(jsonPath("$.discountAmount").value(80.00))
            .andExpect(jsonPath("$.payAmount").value(819.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode quote = objectMapper.readTree(quoteBody);

    String preparedBody =
        mockMvc
            .perform(
                post("/api/store/order-traces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of(
                                "userId",
                                10001,
                                "source",
                                "DIRECT",
                                "skuId",
                                1005,
                                "quantity",
                                1,
                                "userCouponId",
                                userCouponId,
                                "expectedPayAmount",
                                quote.path("payAmount").decimalValue()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String traceId = objectMapper.readTree(preparedBody).path("traceId").asText();
    String orderBody =
        mockMvc
            .perform(
                post("/api/store/orders/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of("userId", 10001, "traceId", traceId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.orderId").value(org.hamcrest.Matchers.startsWith("WW")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(quote.path("discountAmount").decimalValue()).isEqualByComparingTo("80.00");
    String orderId = objectMapper.readTree(orderBody).path("orderId").asText();

    mockMvc
        .perform(
            post("/api/store/orders/{orderId}/cancel", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":10001}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    String ordersBody =
        mockMvc
            .perform(get("/api/store/orders").param("userId", "10001"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode cancelledOrder =
        objectMapper
            .readTree(ordersBody)
            .path("items")
            .valueStream()
            .filter(order -> orderId.equals(order.path("id").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(cancelledOrder.path("status").asText()).isEqualTo("CANCELLED");

    String couponsBody =
        mockMvc
            .perform(get("/api/store/coupons").param("userId", "10001"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode returnedCoupon =
        objectMapper
            .readTree(couponsBody)
            .path("items")
            .valueStream()
            .filter(coupon -> coupon.path("id").asLong() == 3002L)
            .findFirst()
            .orElseThrow();
    assertThat(returnedCoupon.path("userStatus").asText()).isEqualTo("AVAILABLE");
  }
}
