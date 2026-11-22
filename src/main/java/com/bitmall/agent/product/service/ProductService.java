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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 商品展示查询的业务入口。 组装稳定缓存键并调用 CatalogCache；只有缓存没有结果时，才交给 ProductSearchService 或 ProductRepository
 * 查询数据库。
 */
@Service
@RequiredArgsConstructor
public class ProductService {
  /** 缓存未命中时从数据库读取商品资料。 */
  private final ProductRepository mapper;

  /** 把用户搜索条件交给查询仓储，返回分页商品和匹配 SKU。 */
  private final ProductSearchService catalog;

  /** 商品详情和搜索结果走多级缓存，未命中时才回源数据库。 */
  private final CatalogCache cache;

  /** 读取商品详情和搜索结果的缓存时长。 */
  private final CatalogCacheProperties cacheProperties;

  /** 把搜索请求序列化后计算哈希，作为搜索结果的缓存 key。 */
  private final ObjectMapper json;

  /** 批量读取 SKU 公开价格和可售量，与稳定商品资料组成详情。 */
  private final SkuDisplayService offers;

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
    if (productId <= 0) {
      throw new IllegalArgumentException("商品 ID 必须为正整数");
    }
    CatalogCache.DetailValue value =
        cache.get(
            "product:" + productId,
            CatalogCache.DetailValue.class,
            cacheProperties.getDetailTtl(),
            () ->
                new CatalogCache.DetailValue(
                    mapper.findProfile(productId).map(catalog::enrich).orElse(null)));
    if (value.getProduct() == null) {
      throw new IllegalArgumentException("商品不存在或已经下架");
    }
    return offers.detail(value.getProduct());
  }
}
