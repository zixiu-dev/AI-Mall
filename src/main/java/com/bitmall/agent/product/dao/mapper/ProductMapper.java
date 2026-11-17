package com.bitmall.agent.product.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.product.dao.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
  /** 只扫描商品 ID，构建防穿透位图。包含下架商品以避免重新上架时漏登记。 */
  java.util.List<Long> selectBloomIds(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectProductRow(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectProductSkus(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectAvailableSku(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectPurchaseItem(
      java.util.Map<String, Object> values);
}
