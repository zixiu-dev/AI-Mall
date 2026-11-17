package com.bitmall.agent.aftersales.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.aftersales.dao.entity.AfterSalesEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface AfterSalesMapper extends BaseMapper<AfterSalesEntity> {
  java.util.List<java.util.Map<String, Object>> findAfterSalesSql(
      java.util.Map<String, Object> values);

  java.util.List<java.util.Map<String, Object>> findAfterSalesByOrderSql(
      java.util.Map<String, Object> values);

  int createAfterSalesSql(java.util.Map<String, Object> values);
}
