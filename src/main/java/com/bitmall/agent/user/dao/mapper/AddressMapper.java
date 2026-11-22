package com.bitmall.agent.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bitmall.agent.user.dao.entity.AddressEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/** MyBatis-Plus 表映射；条件扣减、行锁和状态 CAS 由 XML 显式定义。 */
@Mapper
public interface AddressMapper extends BaseMapper<AddressEntity> {
  List<Map<String, Object>> defaultAddressSql(
      Map<String, Object> values);
}
