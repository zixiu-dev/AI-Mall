package com.bitmall.agent.cart.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.cart.dao.entity.CartEntity;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface CartMapper extends BaseMapper<CartEntity> {
  java.util.List<java.util.Map<String, Object>> listCartSql(java.util.Map<String, Object> values);

  int incrementItemQuantity(java.util.Map<String, Object> values);

  int insertCartItem(java.util.Map<String, Object> values);

  int updateCartItemSql(java.util.Map<String, Object> values);

  int removeCartItemSql(java.util.Map<String, Object> values);
}
