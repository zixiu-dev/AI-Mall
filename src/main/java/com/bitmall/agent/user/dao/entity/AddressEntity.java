package com.bitmall.agent.user.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

/** user_address 表的可写持久化对象，不直接作为接口返回。 */
@TableName("user_address")
@Getter
@Setter
public class AddressEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("user_id")
  private Long userId;

  @TableField("receiver")
  private String receiver;

  @TableField("phone")
  private String phone;

  @TableField("province")
  private String province;

  @TableField("city")
  private String city;

  @TableField("district")
  private String district;

  @TableField("detail")
  private String detail;

  @TableField("is_default")
  private Boolean isDefault;
}
