package com.bitmall.agent.aftersales.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.aftersales.dao.entity.AfterSalesEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface AfterSalesMapper extends BaseMapper<AfterSalesEntity> {
  List<Map<String, Object>> findAfterSalesSql(
      Map<String, Object> values);

  List<Map<String, Object>> findAfterSalesByOrderSql(
      Map<String, Object> values);

  int createAfterSalesSql(Map<String, Object> values);
}
