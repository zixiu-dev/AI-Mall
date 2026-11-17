package com.bitmall.agent.user.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.user.dao.entity.AddressDO;
import com.bitmall.agent.user.dao.mapper.AddressMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 收货地址的数据访问组件。 按用户读取 user_address，默认地址优先；是否可用于本次结算由 CheckoutService 判断。 */
@Repository
public class AddressRepository {
  /** 执行带绑定参数的 MyBatis-Plus Mapper；SQL 位于对应 XML。 */
  private final AddressMapper mapper;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public AddressRepository(AddressMapper mapper) {
    this.mapper = mapper;
  }

  /** 查询当前用户的默认收货地址；没有默认标记时按编号选择首条。 */
  public Optional<AddressDO> defaultAddress(long userId) {
    return RowValues.optional(
        mapper.defaultAddressSql(parameters("userId", userId)).stream()
            .map(
                row ->
                    new AddressDO(
                        RowValues.longValue(row, "id"),
                        RowValues.longValue(row, "user_id"),
                        RowValues.string(row, "receiver"),
                        RowValues.string(row, "phone"),
                        RowValues.string(row, "province"),
                        RowValues.string(row, "city"),
                        RowValues.string(row, "district"),
                        RowValues.string(row, "detail"),
                        RowValues.bool(row, "is_default")))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static java.util.Map<String, Object> parameters(Object... pairs) {
    java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
    return values;
  }
}
