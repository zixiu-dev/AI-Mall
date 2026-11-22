package com.bitmall.agent.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.order.dao.entity.OrderEventReceiptEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface OrderEventReceiptMapper extends BaseMapper<OrderEventReceiptEntity> {
  int insertSql(Map<String, Object> values);
}
