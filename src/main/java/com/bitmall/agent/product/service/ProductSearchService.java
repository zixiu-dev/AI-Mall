package com.bitmall.agent.product.service;

import com.bitmall.agent.product.dao.repository.ProductSearchRepository;
import com.bitmall.agent.product.dto.ProductSearchRequest;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.bitmall.agent.product.vo.ProductSearchResultVO;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商品搜索的数据库读取服务。 搜索时开启只读事务，但这个事务位于缓存回源函数内部，所以命中缓存时无需先进入事务。 */
@Service
public class ProductSearchService {
  /** 依赖组件：结构化商品搜索的数据访问组件。 */
  private final ProductSearchRepository mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ProductSearchService(ProductSearchRepository mapper) {
    this.mapper = mapper;
  }

  /** 在只读事务中执行数据库搜索；仅在商品缓存回源时调用。 */
  @Transactional(readOnly = true)
  public ProductSearchResultVO.Page search(ProductSearchRequest request) {
    return mapper.search(request);
  }

  /** 为商品详情补齐商品和规格属性。 */
  public ProductDetailVO enrich(ProductDetailVO product) {
    return mapper.enrich(product);
  }

  /** 查询当前启用目录中的可选筛选值。 */
  public Map<String, List<String>> options(String category) {
    return mapper.options(category);
  }
}
