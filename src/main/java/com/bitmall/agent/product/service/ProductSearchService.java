package com.bitmall.agent.product.service;

import com.bitmall.agent.product.cache.ProductProfile;
import com.bitmall.agent.product.dao.repository.ProductSearchRepository;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商品搜索的数据库读取服务。 搜索时开启只读事务，但这个事务位于缓存回源函数内部，所以命中缓存时无需先进入事务。 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {
  /** 执行带预算、规格和库存条件的商品分页搜索。 */
  private final ProductSearchRepository mapper;

  /** 在只读事务中执行数据库搜索；仅在商品缓存回源时调用。 */
  @Transactional(readOnly = true)
  public ProductSearchResultVO.Page search(ProductSearchRequest request) {
    return mapper.search(request);
  }

  /** 为商品详情补齐商品和规格属性。 */
  public ProductProfile enrich(ProductProfile product) {
    return mapper.enrich(product);
  }

  /** 查询当前启用目录中的可选筛选值。 */
  public Map<String, List<String>> options(String category) {
    return mapper.options(category);
  }
}
