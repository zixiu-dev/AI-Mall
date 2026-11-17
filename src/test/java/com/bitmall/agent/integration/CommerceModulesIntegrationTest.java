package com.bitmall.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bitmall.agent.aftersales.vo.AfterSalesVO;
import com.bitmall.agent.agent.service.chat.AgentChatService;
import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import com.bitmall.agent.agent.service.tool.ToolRegistry;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 跨业务模块的集成测试。 验证购物车结算、金额变化拦截、确认期间的聊天限制和售后确认后的真实落库结果。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:commerce-modules;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CommerceModulesIntegrationTest {
  /** 依赖组件：处理用户点击确认或取消之后的流程。 */
  @Autowired com.bitmall.agent.agent.service.action.AgentActionService actions;

  /** 本机模拟 HTTP 请求工具，在测试中验证真实 Controller 和服务调用链。 */
  @Autowired MockMvc mvc;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  @Autowired ObjectMapper json;

  /** 执行带绑定参数的 SQL 查询和更新；只在 Mapper 中使用。 */
  @Autowired JdbcClient jdbc;

  /** 依赖组件：一轮聊天的总调度服务。 */
  @Autowired AgentChatService agent;

  /** 依赖组件：聚合所有 ToolProvider 的工具目录。 */
  @Autowired ToolRegistry tools;

  /** 验证金额变化时不扣库存，正确金额只结算已勾选项，取消后库存恢复。 */
  @Test
  void cartCheckoutRejectsChangedAmountAndOnlyPurchasesSelectedItems() throws Exception {
    jdbc.sql("DELETE FROM cart_item WHERE user_id=10001").update();
    int stock = stock(1003);
    JsonNode cart = postJson("/api/store/cart", Map.of("skuId", 1003, "quantity", 2));
    long selectedId = cart.path("items").get(0).path("id").asLong();
    cart = postJson("/api/store/cart", Map.of("skuId", 1004, "quantity", 1));
    long unselectedId =
        cart.path("items")
            .valueStream()
            .filter(item -> item.path("skuId").asLong() == 1004)
            .findFirst()
            .orElseThrow()
            .path("id")
            .asLong();
    mvc.perform(
            patch("/api/store/cart/{id}", unselectedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("quantity", 1, "selected", false))))
        .andExpect(status().isOk());

    JsonNode quote = postJson("/api/store/checkout/quote", Map.of("source", "CART"));
    assertThat(quote.path("source").asText()).isEqualTo("CART");
    assertThat(quote.path("items").size()).isEqualTo(1);
    assertThat(quote.path("items").get(0).path("id").asLong()).isEqualTo(selectedId);
    assertThat(quote.path("payAmount").decimalValue()).isEqualByComparingTo("798");
    assertThat(quote.path("addressId").asLong()).isEqualTo(20001);

    mvc.perform(
            post("/api/store/order-traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("source", "CART", "expectedPayAmount", 1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("结算金额已经变化")));
    assertThat(stock(1003)).isEqualTo(stock);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM cart_item WHERE user_id=10001")
                .query(Integer.class)
                .single())
        .isEqualTo(2);

    JsonNode prepared =
        postJson(
            "/api/store/order-traces",
            Map.of("source", "CART", "expectedPayAmount", quote.path("payAmount").decimalValue()));
    JsonNode order =
        postJson("/api/store/orders/submit", Map.of("traceId", prepared.path("traceId").asText()));
    String orderId = order.path("orderId").asText();
    assertThat(stock(1003)).isEqualTo(stock - 2);
    mvc.perform(get("/api/store/cart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(unselectedId))
        .andExpect(jsonPath("$.items[0].selected").value(false));
    postJson("/api/store/orders/" + orderId + "/cancel", Map.of());
    assertThat(stock(1003)).isEqualTo(stock);
  }

  /** 验证普通和流式聊天都会被同一张待确认卡拦截，拒绝后能够继续聊天。 */
  @Test
  void pendingActionBlocksBothChatPathsUntilRejectedAndThenConversationContinues() {
    var first = agent.chat(10001, null, "请帮我购买 SKU 1003");
    String conversationId = first.getConversationId();
    String actionId = first.getPendingAction().getId();
    var blocked = agent.chat(10001, conversationId, "再买一个");
    assertThat(blocked.getPendingAction().getId()).isEqualTo(actionId);

    var events = agent.chatStream(10001, conversationId, "换一款").collectList().block();
    assertThat(events)
        .extracting(event -> event.getType())
        .containsExactly("start", "delta", "done");
    var streamed = events.get(2).getReply();
    assertThat(streamed.getPendingAction().getId()).isEqualTo(actionId);
    assertThat(streamed.getContent()).isEqualTo(blocked.getContent());
    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM chat_message WHERE"
                        + " conversation_id=:id AND role='user'")
                .param("id", conversationId)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM pending_action WHERE conversation_id=:id")
                .param("id", conversationId)
                .query(Integer.class)
                .single())
        .isEqualTo(1);

    actions.confirm(10001, actionId, false);
    var nextEvents = agent.chatStream(10001, conversationId, "推荐电脑").collectList().block();
    var completed = nextEvents.get(nextEvents.size() - 1).getReply();
    assertThat(completed.getPendingAction()).isNull();
    assertThat(completed.getContent()).contains("NovaBook");
    assertThat(
            jdbc.sql("SELECT content FROM chat_message WHERE id=:id")
                .param("id", completed.getMessageId())
                .query(String.class)
                .single())
        .isEqualTo(completed.getContent());
  }

  /** 验证售后确认前不写业务表，确认后保存申请和执行成功标记。 */
  @Test
  void confirmedAfterSalesToolPersistsTypedCommandAndCanQueryItsResult() {
    var proposal = agent.chat(10001, null, "帮我申请退货，订单 BM202610010001，耳机坏了");
    assertThat(proposal.getPendingAction().getToolName()).isEqualTo("after_sales_submit");
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM after_sales WHERE order_id='BM202610010001'")
                .query(Integer.class)
                .single())
        .isZero();
    var completed = actions.confirm(10001, proposal.getPendingAction().getId(), true);
    assertThat(completed.getContent()).contains("售后申请已经提交");
    var result =
        (AfterSalesVO)
            tools
                .require("after_sales_query")
                .getHandler()
                .execute(
                    new ToolExecutionContext(
                        10001, proposal.getConversationId(), "query-after-sales"),
                    Map.of("orderId", "BM202610010001"));
    assertThat(result.getType()).isEqualTo("RETURN");
    assertThat(result.getRefundAmount()).isEqualByComparingTo(new BigDecimal("819"));
    assertThat(result.getStatus()).isEqualTo("REVIEWING");
    assertThat(
            jdbc.sql("SELECT status FROM pending_action WHERE id=:id")
                .param("id", proposal.getPendingAction().getId())
                .query(String.class)
                .single())
        .isEqualTo("EXECUTED");
  }

  /** 直接读取规格库存，作为交易前后断言的数据库依据。 */
  private int stock(long skuId) {
    return jdbc.sql("SELECT stock FROM product_sku WHERE id=:id")
        .param("id", skuId)
        .query(Integer.class)
        .single();
  }

  /** 向本机测试接口提交 JSON 并解析成功响应，减少流程测试中的重复代码。 */
  private JsonNode postJson(String path, Map<String, Object> body) throws Exception {
    String result =
        mvc.perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(result);
  }
}
