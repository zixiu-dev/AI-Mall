package com.bitmall.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bitmall.agent.agent.config.AgentProperties;
import com.bitmall.agent.agent.dao.entity.ChatMessageDO;
import com.bitmall.agent.agent.dto.ChatRequest;
import com.bitmall.agent.agent.dto.ConfirmRequest;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.config.HotKeyProperties;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.ObjectMapper;

/** 普通 Java 数据类的兼容性测试。 转换类声明后，JSON 字段、参数校验、配置绑定、值比较和分页内嵌对象仍必须保持可用。 */
class PlainJavaModelsTest {
  /** 测试使用的 JSON 转换器，自动加载日期时间等模块。 */
  private final ObjectMapper json =
      tools.jackson.databind.json.JsonMapper.builder()
          .findAndAddModules()
          .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  /** 验证消息可以通过显式构造方法恢复，标准 Getter 不会改变对外字段名。 */
  @Test
  void chatMessageKeepsJsonFieldsAndValueEquality() throws Exception {
    ChatMessageDO original =
        new ChatMessageDO(
            12L,
            "conversation-1",
            "user",
            "查询订单",
            Map.of("source", "page"),
            LocalDateTime.of(2026, 11, 5, 12, 0));
    String payload = json.writeValueAsString(original);
    ChatMessageDO restored = json.readValue(payload, ChatMessageDO.class);

    assertThat(restored).isEqualTo(original);
    assertThat(restored.hashCode()).isEqualTo(original.hashCode());
    assertThat(restored.getContent()).isEqualTo("查询订单");
    assertThat(json.readTree(payload).properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder(
            "id", "conversationId", "role", "content", "metadata", "createdAt");
  }

  /** 验证分页中的泛型商品列表、筛选条件和 hasMore 字段都能正确恢复。 */
  @Test
  void nestedSearchResultsKeepTheirJsonContract() throws Exception {
    String payload =
        """
{
  "items": [{"id":108,"name":"跑鞋","matchedSkuId":1010,"attributes":{"color":"灰色"}}],
  "total":1,"page":1,"limit":4,"hasMore":false,
  "keywords":["跑鞋"],
  "filters":{"query":"跑鞋","page":1,"limit":4,"inStock":true},
  "suggestions":[]
}
""";
    ProductSearchResultVO.Page page = json.readValue(payload, ProductSearchResultVO.Page.class);
    assertThat(page.getItems().get(0).getMatchedSkuId()).isEqualTo(1010);
    assertThat(page.getFilters().isInStock()).isTrue();
    assertThat(page.getHasMore()).isFalse();

    String encoded = json.writeValueAsString(page);
    assertThat(json.readTree(encoded).has("hasMore")).isTrue();
    assertThat(json.readTree(encoded).has("isHasMore")).isFalse();
    assertThat(json.readValue(encoded, ProductSearchResultVO.Page.class)).isEqualTo(page);
  }

  /** 验证校验注解迁移到字段后，空白聊天和缺少决定的确认请求仍会被拒绝。 */
  @Test
  void requestConstraintsStillApplyToOrdinaryFields() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new ChatRequest(10001L, null, "   "))).hasSize(1);
      assertThat(validator.validate(new ConfirmRequest(10001L, null))).hasSize(1);
      assertThat(validator.validate(new ChatRequest(null, null, "你好"))).isEmpty();
      assertThat(validator.validate(new ConfirmRequest(null, false))).isEmpty();
    }
  }

  /** 验证 Spring 能通过普通构造方法绑定三组配置，原来的默认值继续生效。 */
  @Test
  void configurationBindingRetainsDefaultsAndDurations() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "app.agent.ai-enabled", "true",
                    "app.agent.max-iterations", "0",
                    "app.hotkey.enabled", "true",
                    "app.hotkey.app-name", "test-mall",
                    "app.catalog-cache.enabled", "true",
                    "app.catalog-cache.detail-ttl", "3s")));
    AgentProperties agent = binder.bind("app.agent", AgentProperties.class).get();
    HotKeyProperties hotkey = binder.bind("app.hotkey", HotKeyProperties.class).get();
    CatalogCacheProperties cache =
        binder.bind("app.catalog-cache", CatalogCacheProperties.class).get();

    assertThat(agent.isAiEnabled()).isTrue();
    assertThat(agent.getMaxIterations()).isEqualTo(12);
    assertThat(agent.getRequestTimeoutSeconds()).isEqualTo(180);
    assertThat(hotkey.getAppName()).isEqualTo("test-mall");
    assertThat(hotkey.getPushPeriodMillis()).isEqualTo(500);
    assertThat(cache.getDetailTtl()).isEqualTo(Duration.ofSeconds(3));
    assertThat(cache.getLocalMaximumSize()).isEqualTo(2000);
  }

  /** 防止后续开发重新引入用户不熟悉的数据类简写；嵌套类也要使用普通 class。 */
  @Test
  void projectUsesOnlyOrdinaryClassDeclarations() throws Exception {
    Pattern declaration = Pattern.compile("\\brecord\\s+\\w+\\s*\\(");
    try (var files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertThat(declaration.matcher(Files.readString(file)).find())
            .as("%s 应使用普通 Java 类", file)
            .isFalse();
      }
    }
  }
}
