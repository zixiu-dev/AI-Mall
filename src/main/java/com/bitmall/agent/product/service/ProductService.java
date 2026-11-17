package com.bitmall.agent.product.service;

import com.bitmall.agent.product.cache.CatalogCache;
import com.bitmall.agent.product.config.CatalogCacheProperties;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.vo.ProductCardVO;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品展示查询的业务入口。 组装稳定缓存键并调用 CatalogCache；只有缓存没有结果时，才交给 ProductSearchService 或 ProductRepository
 * 查询数据库。
 */
@Service
public class ProductService {
  /** 依赖组件：商品和规格的基础数据访问组件。 */
  private final ProductRepository mapper;

  /** 依赖组件：商品搜索的数据库读取服务。 */
  private final ProductSearchService catalog;

  /** 依赖组件：商品公共查询的多级缓存入口。 */
  private final CatalogCache cache;

  /** 依赖组件：读取 app.catalog-cache 配置。 */
  private final CatalogCacheProperties cacheProperties;

  /** JSON 转换工具，用于对象与 JSON 字符串互转，不负责调用模型。 */
  private final ObjectMapper json;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ProductService(
      ProductRepository mapper,
      ProductSearchService catalog,
      CatalogCache cache,
      CatalogCacheProperties cacheProperties,
      ObjectMapper json) {
    this.mapper = mapper;
    this.catalog = catalog;
    this.cache = cache;
    this.cacheProperties = cacheProperties;
    this.json = json;
  }

  /** 按完整搜索条件生成缓存键，通过缓存入口查询分页结果。 */
  public ProductSearchResultVO.Page searchCatalog(ProductSearchRequest request) {
    return cache.get(
        "search:" + searchKey(request),
        ProductSearchResultVO.Page.class,
        cacheProperties.getSearchTtl(),
        () -> catalog.search(request));
  }

  /** 对规范化请求 JSON 计算 SHA-256，避免不同预算、规格或页码共用缓存。 */
  private String searchKey(ProductSearchRequest request) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
    } catch (Exception ex) {
      throw new IllegalStateException("搜索缓存键生成失败", ex);
    }
  }

  /** 读取当前品类的品牌、颜色、尺码等筛选选项。 */
  public Map<String, List<String>> searchOptions(String category) {
    return catalog.options(category);
  }

  /** 把简单关键词查询包装成完整结构化请求，再取页面卡片数据。 */
  public List<ProductCardVO> search(String query, String category, int limit) {
    return searchCatalog(
            new ProductSearchRequest(
                query,
                category,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "recommended",
                1,
                Math.max(1, Math.min(limit, 30))))
        .getItems()
        .stream()
        .map(ProductSearchResultVO.Hit::card)
        .toList();
  }

  /** 检查商品编号，通过缓存查询详情；不存在时用包装对象短暂缓存空结果。 */
  public ProductDetailVO product(long productId) {
    if (productId <= 0) throw new IllegalArgumentException("商品 ID 必须为正整数");
    CatalogCache.DetailValue value =
        cache.get(
            "product:" + productId,
            CatalogCache.DetailValue.class,
            cacheProperties.getDetailTtl(),
            () ->
                new CatalogCache.DetailValue(
                    mapper.findProduct(productId).map(catalog::enrich).orElse(null)));
    if (value.getProduct() == null) throw new IllegalArgumentException("商品不存在或已经下架");
    return value.getProduct();
  }
}
