package com.bitmall.agent.agent.service.runtime;

import static org.assertj.core.api.Assertions.*;

import com.bitmall.agent.agent.service.chat.AgentChatService;
import com.bitmall.agent.agent.service.state.AgentStateStoreService;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/** 使用脚本模型驱动真实 Spring AI 流程的集成测试。 模型回答可控，但工具、中间件、状态恢复、确认和数据库业务都使用项目实现。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest(
    properties = {
      "app.agent.ai-enabled=true",
      "spring.datasource.url=jdbc:h2:mem:spring-ai-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("demo")
class SpringAiIntegrationTest {
  @org.springframework.beans.factory.annotation.Autowired
  com.bitmall.agent.order.service.OrderTraceService orderTraces;

  /** 依赖组件：处理用户点击确认或取消之后的流程。 */
  @Autowired com.bitmall.agent.agent.service.action.AgentActionService actions;

  /** 依赖组件：一轮聊天的总调度服务。 */
  @Autowired AgentChatService orchestrator;

  /** 测试脚本模型，按预设步骤返回可控的文本或工具调用。 */
  @Autowired ScriptedModel model;

  /** 执行带绑定参数的 SQL 查询和更新；只在 Mapper 中使用。 */
  @Autowired JdbcClient jdbc;

  /** 依赖组件：Spring AI 原生状态存储适配器。 */
  @Autowired AgentStateStoreService store;

  /** 依赖组件：整理较早的聊天内容。 */
  @Autowired com.bitmall.agent.agent.service.conversation.ConversationSummaryService summaries;

  /** 仅在模型集成测试中生效的 Spring 配置。 提供 ScriptedModel 替代网络模型，避免测试依赖外部模型响应。 */
  @TestConfiguration
  static class Config {
    /** 为测试创建脚本模型，替代真实模型网络请求。 */
    @Bean
    @Primary
    ScriptedModel scriptedModel() {
      return new ScriptedModel();
    }
  }

  /** 按队列返回预先编排回复的测试模型。 每一步可以检查本轮模型看到哪些消息和工具，再决定返回文本还是工具调用。 */
  static class ScriptedModel implements ChatModel {
    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
      return ToolCallingChatOptions.builder().build();
    }

    /** 按执行顺序排列的测试模型步骤，既检查输入，也决定下一条回复。 */
    final Deque<BiFunction<List<Message>, List<ToolDefinition>, ChatResponse>> steps =
        new ArrayDeque<>();

    @Override
    public ChatResponse call(Prompt prompt) {
      return stream(prompt).blockLast();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.defer(
          () -> {
            var step = steps.poll();
            if (step == null)
              return Flux.error(new IllegalStateException("Unexpected model round"));
            var options = (ToolCallingChatOptions) prompt.getOptions();
            return Flux.just(
                step.apply(
                    prompt.getInstructions(),
                    options.getToolCallbacks().stream().map(t -> t.getToolDefinition()).toList()));
          });
    }
  }

  /** 让模型连续调用搜索，验证大结果仍是可解析 JSON 并保留预算与规格。 */
  @Test
  void searchToolPreservesStructuredResultsWhenLargeAndAcceptsBudgetFilters() {
    model.steps.add((messages, tools) -> call("product_search", Map.of("limit", 30)));
    model.steps.add(
        (messages, tools) -> {
          var result = results(messages).get(0);
          assertThat(result.success()).isTrue();
          String output = result.text();
          try {
            var data = new tools.jackson.databind.ObjectMapper().readTree(output).path("data");
            assertThat(data.path("items").isArray()).isTrue();
            assertThat(data.path("total").asInt()).isEqualTo(32);
            assertThat(data.path("truncated").asBoolean()).isTrue();
          } catch (Exception e) {
            throw new AssertionError(e);
          }
          return call(
              "product_search",
              Map.of(
                  "query",
                  "跑鞋",
                  "maxPrice",
                  500,
                  "color",
                  "黑色",
                  "size",
                  "42",
                  "inStock",
                  true,
                  "limit",
                  3));
        });
    model.steps.add(
        (messages, tools) -> {
          var result = results(messages).get(1);
          assertThat(result.success()).isTrue();
          String output = result.text();
          assertThat(output).contains("matchedSkuId", "黑色 / 42 码", "maxPrice");
          return answer("找到符合预算和尺码的商品");
        });
    var events = streamEvents(10001, null, "看看商品，预算500元黑色42码跑鞋");
    assertThat(events.get(events.size() - 1).getReply().getContent()).contains("符合预算");
  }

  /** 同步收集服务回调，沿用工具权限、确认和交易断言。 */
  private List<com.bitmall.agent.agent.vo.StreamEventVO> streamEvents(
      long user, String conversation, String message) {
    var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    try (AgentExecution execution = new AgentExecution(timer, 180)) {
      List<com.bitmall.agent.agent.vo.StreamEventVO> events = new ArrayList<>();
      orchestrator.chatStream(user, conversation, message, execution, events::add);
      return events;
    } finally {
      timer.shutdownNow();
    }
  }

  /** 工具开始前断开，停止执行且不发送 done；下一轮恢复未配对调用，不重放工具。 */
  @Test
  void cancelledStreamStopsBeforeToolAndRepairsHistoryOnNextRound() {
    model.steps.add((messages, tools) -> call("order_query", Map.of("limit", 1)));
    var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    List<com.bitmall.agent.agent.vo.StreamEventVO> events = new ArrayList<>();
    try (AgentExecution execution = new AgentExecution(timer, 180)) {
      assertThatThrownBy(
              () ->
                  orchestrator.chatStream(
                      10001,
                      null,
                      "查订单",
                      execution,
                      event -> {
                        events.add(event);
                        if ("tool_start".equals(event.getType())) execution.cancel();
                      }))
          .isInstanceOf(java.util.concurrent.CancellationException.class);
    } finally {
      timer.shutdownNow();
    }
    assertThat(events)
        .extracting(e -> e.getType())
        .contains("tool_start")
        .doesNotContain("tool_end", "done");
    String conversation = events.get(0).getConversationId();
    model.steps.add(
        (messages, tools) -> {
          assertThat(results(messages))
              .anySatisfy(
                  result -> {
                    assertThat(result.getName()).isEqualTo("order_query");
                    assertThat(result.text()).contains("未自动重放");
                  });
          return answer("上次已中断，可以继续查询");
        });
    assertThat(orchestrator.chat(10001, conversation, "继续").getContent()).contains("已中断");
    assertThat(model.steps).isEmpty();
  }

  /** 在每个测试之前清理控制状态，避免前一个测试影响本次断言。 */
  @BeforeEach
  void reset() {
    model.steps.clear();
  }

  /** 构造一条模型工具调用回复，使用随机调用编号保证调用和结果能配对。 */
  static ChatResponse call(String name, Map<String, Object> args) {
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder()
                    .content("")
                    .toolCalls(
                        List.of(
                            new AssistantMessage.ToolCall(
                                UUID.randomUUID().toString(),
                                "function",
                                name,
                                new ObjectMapper().writeValueAsString(args))))
                    .build())));
  }

  static ChatResponse answer(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  static List<String> names(List<ToolDefinition> tools) {
    return tools.stream().map(ToolDefinition::name).toList();
  }

  /** 在测试中将 Spring AI 结果转换成便于沿用业务断言的视图；不引入旧 SDK。 */
  static List<ResultView> results(List<Message> messages) {
    return messages.stream()
        .filter(m -> m instanceof ToolResponseMessage)
        .map(m -> (ToolResponseMessage) m)
        .flatMap(m -> m.getResponses().stream().map(r -> new ResultView(r, m.getMetadata())))
        .toList();
  }

  static final class ResultView {
    final ToolResponseMessage.ToolResponse result;
    final Map<String, Object> metadata;

    ResultView(ToolResponseMessage.ToolResponse result, Map<String, Object> metadata) {
      this.result = result;
      this.metadata = metadata;
    }

    boolean success() {
      return new ObjectMapper().readTree(result.responseData()).path("ok").asBoolean();
    }

    String getName() {
      return result.name();
    }

    String text() {
      return result.responseData();
    }

    String getOutput() {
      return result.responseData();
    }

    Map<String, Object> getMetadata() {
      Object markers = metadata.get("loadedSkillsByCall");
      if (markers instanceof Map<?, ?> m && m.containsKey(result.id()))
        return Map.of("wowo_loaded_skill", m.get(result.id()));
      return metadata;
    }
  }

  /** 依赖组件：订单交易业务服务。 */
  @Autowired com.bitmall.agent.order.service.OrderService orders;

  /** 依赖组件：优惠券查询、预览和领取的业务服务。 */
  @Autowired com.bitmall.agent.coupon.service.CouponService coupons;

  /** 依赖组件：技能目录和会话技能关联表的数据访问组件。 */
  @Autowired com.bitmall.agent.agent.dao.repository.SkillRepository skills;

  /** 依赖组件：工具调用的统一分发入口。 */
  @Autowired com.bitmall.agent.agent.service.tool.ToolDispatcher dispatcher;

  /** 创建独立订单作为取消测试对象，不修改种子订单。 */
  private String createCancelTestOrder() {
    return orders
        .createOrder(
            10001,
            orderTraces
                .prepare(
                    10001,
                    new com.bitmall.agent.order.model.CreateOrderCommand(
                        "DIRECT", 1003L, 1, 20001L, null, new java.math.BigDecimal("399")),
                    null)
                .getTraceId())
        .get("orderId")
        .toString();
  }

  /** 编排加载取消技能、查订单、提出取消和返回确认卡的模型步骤。 */
  private com.bitmall.agent.agent.vo.AgentReplyVO prepareCancellation(String id) {
    model.steps.add(
        (messages, tools) -> call("load_skill", Map.of("skillCode", "order_cancel_flow")));
    model.steps.add(
        (messages, tools) -> {
          assertThat(names(tools))
              .contains("order_query", "order_cancel")
              .doesNotContain("order_create");
          return call("order_query", Map.of("orderId", id));
        });
    model.steps.add((messages, tools) -> call("order_cancel", Map.of("orderId", id)));
    model.steps.add((messages, tools) -> answer("请核对下方确认卡"));
    var events = streamEvents(10001, null, "取消订单 " + id);
    return events.get(events.size() - 1).getReply();
  }

  /** 只开放取消技能相关工具，确认后库存返还一次，重复确认不能再返还。 */
  @Test
  void cancellationSkillIsIsolatedAndCancelRestoresStockOnlyOnce() {
    String id = createCancelTestOrder();
    int stock =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single();
    var reply = prepareCancellation(id);
    assertThat(reply.getPendingAction()).isNotNull();
    assertThat(reply.getContent()).contains("确认取消订单", "暂不取消");
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("PENDING_PAYMENT");
    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    "order_create",
                    new com.bitmall.agent.agent.service.tool.ToolExecutionContext(
                        10001, reply.getConversationId(), "isolation-test"),
                    Map.of()))
        .hasMessageContaining("需要先加载技能");
    assertThat(actions.confirm(10001, reply.getPendingAction().getId(), true).getContent())
        .doesNotContain("原路退回");
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("CANCELLED");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(stock + 1);
    assertThatThrownBy(() -> actions.confirm(10001, reply.getPendingAction().getId(), true))
        .hasMessageContaining("已经处理");
    assertThatThrownBy(() -> orders.previewCancelOrder(10001, id)).hasMessageContaining("不允许直接取消");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(stock + 1);
    assertThat(skills.find("order_ops")).isEmpty();
  }

  /** 确认卡生成后把订单改为已发货，验证执行时重新校验且不返库存。 */
  @Test
  void cancellationRechecksStateAfterCardAndDoesNotReturnStockIfShipped() {
    String id = createCancelTestOrder();
    var reply = prepareCancellation(id);
    int stock =
        jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single();
    jdbc.sql("UPDATE customer_order SET status='SHIPPED' WHERE id=:id").param("id", id).update();
    assertThat(actions.confirm(10001, reply.getPendingAction().getId(), true).getContent())
        .contains("执行失败");
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("SHIPPED");
    assertThat(
            jdbc.sql("SELECT stock FROM product_sku WHERE id=1003").query(Integer.class).single())
        .isEqualTo(stock);
    assertThatThrownBy(() -> orders.previewCancelOrder(10002, id)).hasMessageContaining("不属于当前用户");
  }

  /** 用户拒绝取消提议时，订单仍保持原状态。 */
  @Test
  void rejectingCancellationLeavesOrderUnchanged() {
    String id = createCancelTestOrder();
    var reply = prepareCancellation(id);
    actions.confirm(10001, reply.getPendingAction().getId(), false);
    assertThat(orders.order(10001, id).getStatus()).isEqualTo("PENDING_PAYMENT");
  }

  /** 验证领券技能隔离，确认卡不等于已领取，确认后才产生用户券。 */
  @Test
  void couponSkillIsIsolatedAndOnlyCreatesCouponAfterConfirmation() {
    long couponId = 3990;
    jdbc.sql(
            """
INSERT INTO coupon(id, name, threshold_amount, discount_amount, total_stock, claimed_count,
                   start_at, end_at, status)
VALUES (:id, '测试领券流程', 100, 10, 2, 0,
        TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE')
""")
        .param("id", couponId)
        .update();
    try {
      model.steps.add((messages, tools) -> call("load_skill", Map.of("skillCode", "coupon_ops")));
      model.steps.add(
          (messages, tools) -> {
            assertThat(names(tools))
                .contains("coupon_claim", "coupon_list")
                .doesNotContain("order_create", "order_cancel");
            assertThat(
                    results(messages).stream()
                        .map(r -> r.getOutput().toString())
                        .toList()
                        .toString())
                .contains("# 优惠券领取办理", "couponId", "userCouponId");
            return call("coupon_list", Map.of());
          });
      model.steps.add((messages, tools) -> call("coupon_claim", Map.of("couponId", couponId)));
      model.steps.add((messages, tools) -> answer("请核对下方确认卡"));

      var events = streamEvents(10001, null, "领取测试领券流程优惠券");
      var reply = events.get(events.size() - 1).getReply();
      assertThat(reply.getPendingAction()).isNotNull();
      assertThat(reply.getPendingAction().getToolName()).isEqualTo("coupon_claim");
      assertThat(reply.getContent()).contains("优惠券尚未领取", "确认领取", "暂不领取");
      assertThat(
              jdbc.sql(
                      "SELECT COUNT(*) FROM user_coupon WHERE user_id=10001" + " AND coupon_id=:id")
                  .param("id", couponId)
                  .query(Long.class)
                  .single())
          .isZero();

      assertThat(actions.confirm(10001, reply.getPendingAction().getId(), true).getContent())
          .contains("优惠券领取成功", "以结算校验为准")
          .doesNotContain("可以直接使用");
      assertThat(
              jdbc.sql(
                      "SELECT COUNT(*) FROM user_coupon WHERE user_id=10001" + " AND coupon_id=:id")
                  .param("id", couponId)
                  .query(Long.class)
                  .single())
          .isEqualTo(1);
      assertThat(
              jdbc.sql("SELECT claimed_count FROM coupon WHERE id=:id")
                  .param("id", couponId)
                  .query(Integer.class)
                  .single())
          .isEqualTo(1);
    } finally {
      jdbc.sql("DELETE FROM user_coupon WHERE coupon_id=:id").param("id", couponId).update();
      jdbc.sql("DELETE FROM coupon WHERE id=:id").param("id", couponId).update();
    }
  }

  /** 尚未开始或已领完的活动，在生成确认卡前就应被拒绝。 */
  @Test
  void couponPreviewRejectsFutureAndSoldOutActivitiesBeforeCreatingCard() {
    jdbc.sql(
            """
INSERT INTO coupon(id, name, threshold_amount, discount_amount, total_stock, claimed_count,
                   start_at, end_at, status) VALUES
(3991, '尚未开始测试券', 0, 1, 1, 0, TIMESTAMP '2027-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE'),
(3992, '已经领完测试券', 0, 1, 1, 1, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE')
""")
        .update();
    try {
      assertThatThrownBy(() -> coupons.previewClaimCoupon(10001, 3991))
          .hasMessageContaining("尚未开始");
      assertThatThrownBy(() -> coupons.previewClaimCoupon(10001, 3992))
          .hasMessageContaining("已经领完");
    } finally {
      jdbc.sql("DELETE FROM coupon WHERE id IN (3991,3992)").update();
    }
  }

  /** 验证同一轮加载技能后可继续调用工具，确认才写入，下一轮能恢复状态。 */
  @Test
  void loadsSkillWithinSameReactLoopAndOnlyWritesAfterConfirmationThenRestoresState() {
    long before = jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single();
    model.steps.add(
        (messages, tools) -> {
          assertThat(names(tools))
              .contains("load_skill", "checkout_quote")
              .doesNotContain("order_create");
          assertThat(
                  tools.stream()
                      .filter(t -> t.name().equals("load_skill"))
                      .findFirst()
                      .orElseThrow()
                      .description())
              .contains("order_create_flow");
          return call("load_skill", Map.of("skillCode", "order_create_flow"));
        });
    model.steps.add(
        (messages, tools) -> {
          assertThat(names(tools))
              .contains("order_create")
              .doesNotContain("order_cancel", "coupon_claim");
          assertThat(results(messages))
              .anySatisfy(
                  r -> {
                    assertThat(r.getName()).isEqualTo("load_skill");
                    assertThat(r.getMetadata())
                        .containsEntry("wowo_loaded_skill", "order_create_flow");
                  });
          return call("checkout_quote", Map.of("skuId", 1003, "quantity", 1, "addressId", 20001));
        });
    model.steps.add(
        (messages, tools) ->
            call(
                "order_create",
                Map.of(
                    "skuId", 1003, "quantity", 1, "addressId", 20001, "expectedPayAmount", 399)));
    model.steps.add(
        (messages, tools) -> {
          assertThat(tools).isEmpty();
          assertThat(
                  results(messages).stream().map(r -> r.getOutput().toString()).toList().toString())
              .contains("AWAITING_CONFIRMATION");
          return answer("回复确认下单即可购买。"); // Even incorrect model instructions cannot replace the
          // UI contract.
        });
    var events = streamEvents(10001, null, "买一个 SKU 1003");
    assertThat(events).isNotNull().anySatisfy(e -> assertThat(e.getType()).isEqualTo("tool_start"));
    var reply = events.get(events.size() - 1).getReply();
    assertThat(reply.getContent()).contains("尚未执行", "点击“确认执行”").doesNotContain("回复确认下单");
    assertThat(reply.getPendingAction()).isNotNull();
    assertThat(jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single())
        .isEqualTo(before);
    assertThat(store.exists("10001", reply.getConversationId())).isTrue();
    assertThat(store.exists("10002", reply.getConversationId())).isFalse();
    assertThat(actions.confirm(10001, reply.getPendingAction().getId(), true).getContent())
        .contains("订单已经创建成功");
    assertThat(jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single())
        .isEqualTo(before + 1);
    assertThatThrownBy(() -> actions.confirm(10001, reply.getPendingAction().getId(), true))
        .hasMessageContaining("已经处理");
    model.steps.add(
        (messages, tools) -> {
          assertThat(names(tools)).contains("order_create");
          assertThat(results(messages)).hasSize(3);
          assertThat(messages.stream().map(Message::getText).toList().toString())
              .contains("商城后端操作回执", "订单已经创建成功");
          return answer("上一笔订单已创建。");
        });
    assertThat(orchestrator.chat(10001, reply.getConversationId(), "刚才的订单呢").getContent())
        .isEqualTo("上一笔订单已创建。");
    assertThat(model.steps).isEmpty();
  }

  /** 故意让模型猜隐藏工具和传错误参数，验证执行入口拦截后允许模型纠正。 */
  @Test
  void rejectsGuessedHiddenToolsAndBadSchemaThenLetsModelCorrect() {
    model.steps.add((messages, tools) -> call("coupon_claim", Map.of("couponId", 3001)));
    model.steps.add(
        (messages, tools) -> {
          assertThat(results(messages)).anySatisfy(r -> assertThat(r.success()).isFalse());
          return call("product_detail", Map.of("productId", "not-a-number", "userId", 999));
        });
    model.steps.add(
        (messages, tools) -> {
          assertThat(results(messages).stream().filter(r -> !r.success()).count()).isEqualTo(2);
          return call("product_detail", Map.of("productId", 101));
        });
    model.steps.add((messages, tools) -> answer("已查询商品。"));
    var reply = orchestrator.chat(10001, null, "查看商品");
    assertThat(reply.getContent()).isEqualTo("已查询商品。");
    assertThat(reply.getPendingAction()).isNull();
  }

  /** 裁剪掉技能手册所在轮次后，相关工具必须收回。 */
  @Test
  void evictsSkillsWhenTheirManualLeavesVisibleHistory() {
    model.steps.add((messages, tools) -> call("load_skill", Map.of("skillCode", "coupon_ops")));
    model.steps.add((messages, tools) -> answer("已加载。"));
    String conversation = orchestrator.chat(10001, null, "了解领券流程").getConversationId();
    for (int i = 0; i < 6; i++) {
      boolean expired = i == 5;
      model.steps.add(
          (messages, tools) -> {
            if (expired) {
              assertThat(names(tools)).doesNotContain("coupon_claim");
              assertThat(
                      messages.stream()
                          .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                          .map(Message::getText)
                          .toList()
                          .toString())
                  .contains("了解领券流程");
            } else assertThat(names(tools)).contains("coupon_claim");
            return answer("好的。");
          });
      assertThat(orchestrator.chat(10001, conversation, "继续聊天 " + i).getContent()).isEqualTo("好的。");
    }
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM conversation_skill WHERE conversation_id=:c")
                .param("c", conversation)
                .query(Long.class)
                .single())
        .isZero();
    String summary =
        jdbc.sql("SELECT summary FROM conversation WHERE id=:c")
            .param("c", conversation)
            .query(String.class)
            .single();
    summaries.compactIfNeeded(conversation);
    assertThat(
            jdbc.sql("SELECT summary FROM conversation WHERE id=:c")
                .param("c", conversation)
                .query(String.class)
                .single())
        .isEqualTo(summary);
  }

  /** 真实模型失败后不能自动进入演示交易流程。 */
  @Test
  void providerErrorDoesNotRunDemoBusinessFlow() {
    // Empty script fails like an unavailable provider, never creates an order proposal
    // automatically.
    var reply = orchestrator.chat(10001, null, "购买 SKU 1001");
    assertThat(reply.getContent()).contains("AI 服务暂时不可用");
    assertThat(reply.getPendingAction()).isNull();
  }

  /** 让模型持续调用工具，验证达到次数上限后终止且会话仍可恢复。 */
  @Test
  void endlessToolRequestsHitIterationBudget() {
    for (int i = 0; i < 30; i++)
      model.steps.add((messages, tools) -> call("current_date", Map.of()));
    var reply = orchestrator.chat(10001, null, "反复查日期");
    assertThat(reply.getContent()).contains("执行步数上限");
    assertThat(model.steps.size()).isGreaterThanOrEqualTo(17);
    assertThat(reply.getPendingAction()).isNull();
    model.steps.clear();
    model.steps.add((messages, tools) -> answer("已恢复正常对话。"));
    assertThat(orchestrator.chat(10001, reply.getConversationId(), "停止查询").getContent())
        .isEqualTo("已恢复正常对话。");
  }

  /** 模型虚构确认卡时允许一次纠正，只能生成提议，不能替用户确认交易。 */
  @Test
  void repairsQuotedOrderWithoutRealCardOnceAndDoesNotExecuteTrade() {
    long before = jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single();
    model.steps.add(
        (messages, tools) -> call("load_skill", Map.of("skillCode", "order_create_flow")));
    model.steps.add(
        (messages, tools) -> call("checkout_quote", Map.of("skuId", 1003, "quantity", 1)));
    model.steps.add((messages, tools) -> answer("待确认订单，请点击页面上的「确认执行」按钮完成下单。"));
    model.steps.add(
        (messages, tools) -> {
          assertThat(messages.stream().map(Message::getText).toList().toString()).contains("后台校验");
          return call(
              "order_create",
              Map.of("skuId", 1003, "quantity", 1, "addressId", 20001, "expectedPayAmount", 399));
        });
    model.steps.add((messages, tools) -> answer("请点击确认执行。"));
    var events = streamEvents(10001, null, "买 SKU 1003，一件，不用券");
    var reply = events.get(events.size() - 1).getReply();
    assertThat(reply.getPendingAction()).isNotNull();
    assertThat(reply.getContent()).contains("尚未执行");
    assertThat(jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single())
        .isEqualTo(before);
    assertThat(model.steps).isEmpty();
  }

  /** 模型纠正后仍虚构确认卡时，用明确未生成的提示替换。 */
  @Test
  void repeatedFakeCardIsReplacedAfterOneCorrection() {
    model.steps.add((messages, tools) -> answer("请点击确认执行。"));
    model.steps.add((messages, tools) -> answer("请点击确认执行。"));
    var reply = orchestrator.chat(10001, null, "买鞋");
    assertThat(reply.getPendingAction()).isNull();
    assertThat(reply.getContent()).contains("还没有生成可点击的确认卡", "本次操作尚未执行");
    assertThat(model.steps).isEmpty();
  }

  /** 普通报价不应被误判为虚构确认卡，也不能因此创建交易提议。 */
  @Test
  void ordinaryQuoteDoesNotTriggerRepairOrCreateCard() {
    model.steps.add(
        (messages, tools) -> call("checkout_quote", Map.of("skuId", 1003, "quantity", 1)));
    model.steps.add((messages, tools) -> answer("报价399元，本次只查询，没有创建订单。"));
    var reply = orchestrator.chat(10001, null, "只查 SKU 1003 的价格，不要下单");
    assertThat(reply.getPendingAction()).isNull();
    assertThat(reply.getContent()).contains("报价399元");
    assertThat(model.steps).isEmpty();
  }

  /** 修改已持久化参数后确认，必须被完整性校验拦截。 */
  @Test
  void tamperedStoredArgumentsCannotBeConfirmed() {
    model.steps.add(
        (messages, tools) -> call("load_skill", Map.of("skillCode", "order_create_flow")));
    model.steps.add(
        (messages, tools) ->
            call(
                "order_create",
                Map.of(
                    "skuId", 1003, "quantity", 1, "addressId", 20001, "expectedPayAmount", 399)));
    model.steps.add((messages, tools) -> answer("请确认。"));
    var proposal = orchestrator.chat(10001, null, "买 SKU 1003");
    assertThat(proposal.getPendingAction()).isNotNull();
    long before = jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single();
    jdbc.sql("UPDATE pending_action SET arguments=:args WHERE id=:id")
        .param(
            "args", "{\"skuId\":1003,\"quantity\":2,\"addressId\":20001,\"expectedPayAmount\":399}")
        .param("id", proposal.getPendingAction().getId())
        .update();
    assertThatThrownBy(() -> actions.confirm(10001, proposal.getPendingAction().getId(), true))
        .hasMessageContaining("参数完整性校验失败");
    assertThat(jdbc.sql("SELECT COUNT(*) FROM customer_order").query(Long.class).single())
        .isEqualTo(before);
    assertThat(actions.confirm(10001, proposal.getPendingAction().getId(), false).getContent())
        .contains("已取消这次操作");
  }

  /** 真正执行 ReAct 工具回路，确认结构化意图和来源可见，而不是测试模型背答案。 */
  @Test
  void brandPolicyToolPreservesStructuredFiltersAndCitableSource() {
    model.steps.add(
        (messages, tools) ->
            call(
                "search_knowledge",
                Map.of("query", "iPhone退货政策", "brand", "APPLE", "category", "RETURNS")));
    model.steps.add(
        (messages, tools) -> {
          try {
            var block = results(messages).get(0);
            assertThat(block.success()).isTrue();
            var data =
                new tools.jackson.databind.ObjectMapper().readTree(block.text()).path("data");
            assertThat(data.path("status").asText()).isEqualTo("FOUND");
            var hit = data.path("hits").get(0);
            assertThat(hit.path("brand").asText()).isEqualTo("APPLE");
            assertThat(hit.path("documentType").asText()).isEqualTo("RETURNS");
            assertThat(hit.path("applicableChannel").asText()).contains("Apple Store");
            assertThat(hit.path("sourceCheckedAt").asText()).isEqualTo("2026-11-17");
            return answer(
                hit.path("content").asText() + "\n[官方来源](" + hit.path("sourceUrl").asText() + ")");
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
    var reply = orchestrator.chat(10001, null, "iPhone退货政策");
    assertThat(reply.getPendingAction()).isNull();
    assertThat(reply.getContent())
        .contains("14", "https://www.apple.com.cn/shop/help/returns_refund");
  }

  /** 多品牌结果即使超长也只按完整文档裁剪，仍可解析并明确部分未展示。 */
  @Test
  void largePolicyComparisonKeepsWholeDocumentsAndSources() {
    model.steps.add(
        (messages, tools) -> call("search_knowledge", Map.of("query", "苹果 小米 Nike 宜家 MUJI 的退货规则")));
    model.steps.add(
        (messages, tools) -> {
          try {
            var block = results(messages).get(0);
            assertThat(block.success()).isTrue();
            var payload = new tools.jackson.databind.ObjectMapper().readTree(block.text());
            assertThat(payload.has("preview")).isFalse();
            var data = payload.path("data");
            assertThat(data.path("hits").size()).isPositive();
            for (var hit : data.path("hits")) {
              assertThat(hit.path("sourceUrl").asText()).startsWith("https://");
              assertThat(hit.path("content").asText()).isNotBlank();
            }
            if (data.path("truncated").asBoolean())
              assertThat(data.path("message").asText()).contains("分别继续检索");
            return answer("已分品牌查到政策，未展示的资料需要分别继续查询。");
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
    assertThat(orchestrator.chat(10001, null, "比较苹果 小米 Nike 宜家 MUJI 的退货规则").getPendingAction())
        .isNull();
  }

  /** 同一个 ReAct 循环先收知识证据再查真实订单；没有独立 Planner 或嵌套 RAG Agent。 */
  @Test
  void knowledgeSubQueriesReturnToOuterLoopBeforeOrderLookup() {
    String original = "比较Apple和Nike退货政策，再查我的订单状态";
    model.steps.add(
        (messages, tools) ->
            call(
                "search_knowledge",
                Map.of("query", original, "queries", List.of("Apple退货政策", "Nike退货政策"))));
    model.steps.add(
        (messages, tools) -> {
          var block = results(messages).get(0);
          assertThat(block.success()).isTrue();
          try {
            var data =
                new tools.jackson.databind.ObjectMapper().readTree(block.text()).path("data");
            assertThat(data.path("groups").size()).isEqualTo(2);
            assertThat(data.path("hits").size()).isPositive();
            assertThat(data.path("coverage").path("requested").asInt()).isEqualTo(2);
          } catch (Exception e) {
            throw new AssertionError(e);
          }
          return call("order_query", Map.of());
        });
    model.steps.add(
        (messages, tools) -> {
          assertThat(results(messages).get(0).success()).isTrue();
          return answer("政策按品牌分别说明，订单状态来自订单工具。");
        });
    var result = orchestrator.chat(10001, null, original);
    assertThat(result.getPendingAction()).isNull();
    assertThat(result.getContent()).contains("订单工具");
    assertThat(model.steps).isEmpty();
  }

  /** 嵌套元素类型错误走工具 ERROR；模型可修正，但不能让未校验参数进入检索服务。 */
  @Test
  void knowledgeNestedArrayTypesAreCheckedAtExecution() {
    model.steps.add(
        (messages, tools) ->
            call("search_knowledge", Map.of("query", "苹果退货", "queries", List.of(12))));
    model.steps.add(
        (messages, tools) -> {
          assertThat(results(messages).get(0).success()).isFalse();
          return answer("参数有误，需要修正后查询。");
        });
    assertThat(orchestrator.chat(10001, null, "苹果退货").getPendingAction()).isNull();
  }
}
