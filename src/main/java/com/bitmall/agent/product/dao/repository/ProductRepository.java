package com.bitmall.agent.product.dao.repository;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.product.dao.mapper.ProductMapper;
import com.bitmall.agent.product.vo.ProductDetailVO;
import com.bitmall.agent.product.vo.SkuVO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 商品和规格的基础数据访问组件。 读取 product、product_sku，为详情和结算提供数据库当前值，不计算优惠或处理模型请求。 */
@Repository
public class ProductRepository {
  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final ProductMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public ProductRepository(ProductMapper mapper) {
    this.mapper = mapper;
  }

  /** 查询启用商品及其启用规格，组装完整详情；属性补充由搜索 Mapper 负责。 */
  public Optional<ProductDetailVO> findProduct(long productId) {
    List<ProductDetailVO> products =
        mapper.selectProductRow(parameters("id", productId)).stream()
            .map(
                row ->
                    new ProductDetailVO(
                        RowValues.longValue(row, "id"),
                        RowValues.string(row, "name"),
                        RowValues.string(row, "category"),
                        RowValues.string(row, "brand"),
                        RowValues.string(row, "summary"),
                        RowValues.string(row, "image_key"),
                        RowValues.decimal(row, "rating"),
                        RowValues.intValue(row, "sales_count"),
                        List.of()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    if (products.isEmpty()) {
      return Optional.empty();
    }
    List<SkuVO> skus =
        mapper.selectProductSkus(parameters("productId", productId)).stream()
            .map(
                row ->
                    new SkuVO(
                        RowValues.longValue(row, "id"),
                        RowValues.string(row, "sku_code"),
                        RowValues.string(row, "spec"),
                        RowValues.decimal(row, "price"),
                        RowValues.decimal(row, "original_price"),
                        RowValues.intValue(row, "stock")))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    ProductDetailVO p = products.get(0);
    return Optional.of(
        new ProductDetailVO(
            p.getId(),
            p.getName(),
            p.getCategory(),
            p.getBrand(),
            p.getSummary(),
            p.getImageKey(),
            p.getRating(),
            p.getSalesCount(),
            skus));
  }

  /** 按规格编号读取当前价格和库存。 */
  public Optional<SkuVO> findSku(long skuId) {
    return RowValues.optional(
        mapper.selectAvailableSku(parameters("id", skuId)).stream()
            .map(
                row ->
                    new SkuVO(
                        RowValues.longValue(row, "id"),
                        RowValues.string(row, "sku_code"),
                        RowValues.string(row, "spec"),
                        RowValues.decimal(row, "price"),
                        RowValues.decimal(row, "original_price"),
                        RowValues.intValue(row, "stock")))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
  }

  /** 读取可以购买的具体规格，并按请求数量组装结算项，价格和库存都来自数据库。 */
  public Optional<CartItemVO> findPurchaseItem(long skuId, int quantity) {
    return RowValues.optional(
        mapper.selectPurchaseItem(parameters("quantity", quantity, "skuId", skuId)).stream()
            .map(
                row ->
                    new CartItemVO(
                        RowValues.longValue(row, "id"),
                        RowValues.longValue(row, "sku_id"),
                        RowValues.longValue(row, "product_id"),
                        RowValues.string(row, "product_name"),
                        RowValues.string(row, "spec"),
                        RowValues.string(row, "image_key"),
                        RowValues.decimal(row, "price"),
                        RowValues.intValue(row, "stock"),
                        RowValues.intValue(row, "quantity"),
                        true))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
