package com.bitmall.agent.product.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.product.dao.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
  List<Map<String, Object>> selectOfferRows(
      @Param("ids") List<Long> ids);

  /** 只扫描商品 ID，构建防穿透位图。包含下架商品以避免重新上架时漏登记。 */
  List<Long> selectBloomIds(Map<String, Object> values);

  List<Map<String, Object>> selectProductRow(
      Map<String, Object> values);

  List<Map<String, Object>> selectProductSkus(
      Map<String, Object> values);

  List<Map<String, Object>> selectAvailableSku(
      Map<String, Object> values);

  List<Map<String, Object>> selectPurchaseItem(
      Map<String, Object> values);
}
