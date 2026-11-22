package com.bitmall.agent.cart.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** cart_item 表的可写持久化对象，不直接作为接口返回。 */
@TableName("cart_item")
@Getter
@Setter
public class CartEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("user_id")
  private Long userId;

  @TableField("sku_id")
  private Long skuId;

  @TableField("quantity")
  private Integer quantity;

  @TableField("selected")
  private Boolean selected;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
