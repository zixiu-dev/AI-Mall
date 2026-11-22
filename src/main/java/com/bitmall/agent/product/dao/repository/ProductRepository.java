package com.bitmall.agent.product.dao.repository;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.product.cache.ProductProfile;
import com.bitmall.agent.product.dao.mapper.ProductMapper;
import com.bitmall.agent.product.vo.SkuVO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 商品和规格的基础数据访问组件。 读取 product、product_sku，为详情和结算提供数据库当前值，不计算优惠或处理模型请求。 */
@Repository
@RequiredArgsConstructor
public class ProductRepository {
  /** 商品与 SKU 的 SQL，定义在 mapper/product/ProductMapper.xml。 */
  private final ProductMapper mapper;

  /** 查询启用商品及其启用规格，组装完整详情；属性补充由搜索 Mapper 负责。 */
  public Optional<ProductProfile> findProfile(long productId) {
    return RowValues.optional(mapper.selectProductRow(parameters("id", productId)))
        .map(
            row -> {
              var profile = new ProductProfile();
              profile.id = productId;
              profile.name = RowValues.string(row, "name");
              profile.category = RowValues.string(row, "category");
              profile.brand = RowValues.string(row, "brand");
              profile.summary = RowValues.string(row, "summary");
              profile.imageKey = RowValues.string(row, "image_key");
              profile.rating = RowValues.decimal(row, "rating");
              profile.salesCount = RowValues.intValue(row, "sales_count");
              profile.skus =
                  mapper.selectProductSkus(parameters("productId", productId)).stream()
                      .map(
                          data -> {
                            var sku = new ProductProfile.Variant();
                            sku.id = RowValues.longValue(data, "id");
                            sku.skuCode = RowValues.string(data, "sku_code");
                            sku.spec = RowValues.string(data, "spec");
                            return sku;
                          })
                      .toList();
              return profile;
            });
  }

  /** 一次查询所有未命中规格的公开报价和数据库展示数量。 */
  public List<Map<String, Object>> displayRows(List<Long> ids) {
    return ids.isEmpty() ? List.of() : mapper.selectOfferRows(ids);
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
            .collect(Collectors.toCollection(ArrayList::new)));
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
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
