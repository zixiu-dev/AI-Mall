package com.bitmall.agent.cart.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.cart.dao.entity.CartEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface CartMapper extends BaseMapper<CartEntity> {
  List<Map<String, Object>> listCartSql(Map<String, Object> values);

  int incrementItemQuantity(Map<String, Object> values);

  int insertCartItem(Map<String, Object> values);

  int updateCartItemSql(Map<String, Object> values);

  int removeCartItemSql(Map<String, Object> values);
}
