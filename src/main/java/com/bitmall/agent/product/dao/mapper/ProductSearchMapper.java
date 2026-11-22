package com.bitmall.agent.product.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.product.dao.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ProductSearchMapper extends BaseMapper<ProductEntity> {
  List<Long> countMatches(Map<String, Object> values);

  List<Map<String, Object>> selectMatchingProducts(
      Map<String, Object> values);

  List<String> selectBrands(Map<String, Object> values);

  List<Map<String, Object>> selectAttributeOptions(
      Map<String, Object> values);

  List<Map<String, Object>> selectAttributes(
      Map<String, Object> values);
}
