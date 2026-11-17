package com.bitmall.agent.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bitmall.agent.agent.service.tool.ToolExecutionContext;
import com.bitmall.agent.agent.service.tool.ToolRegistry;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 商品结构化搜索的集成测试。 重点防止预算、颜色、尺码和库存来自不同规格，并验证稳定排序、分页与非法参数处理。 */
@com.bitmall.agent.support.LegacyCatalogFixture
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CatalogSearchIntegrationTest {
  /** 本机模拟 HTTP 请求工具，在测试中验证真实 Controller 和服务调用链。 */
  @Autowired MockMvc mvc;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  @Autowired ObjectMapper json;

  /** 依赖组件：聚合所有 ToolProvider 的工具目录。 */
  @Autowired ToolRegistry tools;

  /** 验证预算、颜色、尺码和库存必须同时匹配同一规格。 */
  @Test
  void constraintsMustMatchOneSkuRatherThanDifferentVariants() throws Exception {
    // Product 129 has a white SKU for 489 and a black SKU for 529: black under 500 must not
    // match.
    var body =
        mvc.perform(
                get("/api/store/products")
                    .param("query", "跑鞋")
                    .param("maxPrice", "500")
                    .param("color", "黑色")
                    .param("size", "42")
                    .param("inStock", "true"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var tree = json.readTree(body);
    assertThat(tree.path("total").asInt()).isGreaterThan(0);
    for (var item : tree.path("items")) {
      assertThat(item.path("id").asInt()).isNotEqualTo(129);
      assertThat(item.path("price").decimalValue())
          .isLessThanOrEqualTo(new java.math.BigDecimal("500"));
      assertThat(item.path("matchedSpec").asText()).contains("黑色", "42");
      assertThat(item.path("stock").asInt()).isPositive();
    }
  }

  /** 缺货尺码不能借用另一尺码的库存混入有货结果。 */
  @Test
  void outOfStockSizeDoesNotBorrowAnotherSkusStock() throws Exception {
    mvc.perform(
            get("/api/store/products")
                .param("brand", "CloudStep")
                .param("size", "44")
                .param("inStock", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/store/products").param("brand", "CloudStep").param("size", "44"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].stock").value(0));
  }

  /** 跨页收集商品，验证先全局排序后分页且没有重复或漏项。 */
  @Test
  void sortsAllResultsBeforePagingAndPreservesStableOrder() throws Exception {
    List<Long> ids = new ArrayList<>();
    double last = -1;
    long total = 0;
    for (int page = 1; page <= 3; page++) {
      var body =
          mvc.perform(
                  get("/api/store/products")
                      .param("sort", "priceAsc")
                      .param("limit", "12")
                      .param("page", "" + page))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      var tree = json.readTree(body);
      total = tree.path("total").asLong();
      for (var item : tree.path("items")) {
        double price = item.path("price").asDouble();
        assertThat(price).isGreaterThanOrEqualTo(last);
        last = price;
        ids.add(item.path("id").asLong());
      }
      assertThat(tree.path("hasMore").asBoolean()).isEqualTo(page < 3);
    }
    assertThat(ids).hasSize((int) total).doesNotHaveDuplicates();
    assertThat(total).isEqualTo(32);
  }

  /** 验证多关键词、同义词、组合词、属性和通配符字面查询。 */
  @Test
  void multiKeywordsSynonymsAttributesAndLiteralWildcards() throws Exception {
    mvc.perform(
            get("/api/store/products")
                .param("query", "运动鞋 缓震")
                .param("material", "网布")
                .param("useCase", "跑步"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords[0]").value("跑鞋"))
        .andExpect(jsonPath("$.items[0].attributes.material").value("网布"));
    mvc.perform(get("/api/store/products").param("query", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(get("/api/store/products").param("query", "缓震跑鞋"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords[0]").value("缓震"))
        .andExpect(jsonPath("$.keywords[1]").value("跑鞋"));
    for (String query : List.of("_", "' OR 1=1 --"))
      mvc.perform(get("/api/store/products").param("query", query))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(0));
  }

  /** 非法预算、页码和排序必须拒绝，没有结果时不能偷偷放宽条件。 */
  @Test
  void rejectsInvalidBudgetsPagesAndSortsWithoutRelaxing() throws Exception {
    mvc.perform(get("/api/store/products").param("minPrice", "500").param("maxPrice", "200"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("maxPrice", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("maxPrice", "1.001"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("limit", "1000"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("sort", "price; DROP TABLE product"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/store/products").param("query", "跑鞋").param("maxPrice", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.filters.maxPrice").value(10))
        .andExpect(jsonPath("$.suggestions").isNotEmpty());
  }

  /** 验证详情与筛选项返回数据库中保存的商品和规格属性。 */
  @Test
  void detailAndOptionsExposeStoredProductAndSkuAttributes() throws Exception {
    mvc.perform(get("/api/store/products/108"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attributes.material").value("网布"))
        .andExpect(jsonPath("$.skus[0].attributes.size").value("41"));
    mvc.perform(get("/api/store/product-filters").param("category", "运动户外"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.brand").value(org.hamcrest.Matchers.hasItem("SwiftRun")))
        .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.hasItem("42")));
  }

  /** 直接调用商品工具，验证它与页面共用结构化搜索和分页结果。 */
  @Test
  void agentUsesSameSearchContractAndReturnsTypedIdsAndPaging() {
    var page =
        (ProductSearchResultVO.Page)
            tools
                .require("product_search")
                .getHandler()
                .execute(
                    new ToolExecutionContext(10001, "search-test", "search-request"),
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
                        2));
    assertThat(page.getItems()).hasSize(2);
    assertThat(page.getHasMore()).isTrue();
    assertThat(page.getItems())
        .allMatch(item -> item.getMatchedSkuId() > 0 && item.getMatchedSpec().contains("42"));
  }
}
