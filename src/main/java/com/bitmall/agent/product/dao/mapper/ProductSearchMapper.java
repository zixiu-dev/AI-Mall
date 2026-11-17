package com.bitmall.agent.product.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.product.dao.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ProductSearchMapper extends BaseMapper<ProductEntity> {
  java.util.List<Long> countMatches(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectMatchingProducts(
      java.util.Map<String, Object> values);

  java.util.List<String> selectBrands(java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectAttributeOptions(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> selectAttributes(
      java.util.Map<String, Object> values);
}
