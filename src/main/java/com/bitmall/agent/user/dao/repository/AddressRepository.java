package com.bitmall.agent.user.dao.repository;

import com.bitmall.agent.common.persistence.RowValues;
import com.bitmall.agent.user.dao.entity.AddressDO;
import com.bitmall.agent.user.dao.mapper.AddressMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 收货地址的数据访问组件。 按用户读取 user_address，默认地址优先；是否可用于本次结算由 CheckoutService 判断。 */
@Repository
@RequiredArgsConstructor
public class AddressRepository {
  /** 收货地址的 SQL，定义在 mapper/user/AddressMapper.xml。 */
  private final AddressMapper mapper;

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
            .collect(Collectors.toCollection(ArrayList::new)));
  }

  /** 参数按原类型绑定，允许可选字段为 null。 */
  private static Map<String, Object> parameters(Object... pairs) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      values.put((String) pairs[i], pairs[i + 1]);
    }
    return values;
  }
}
