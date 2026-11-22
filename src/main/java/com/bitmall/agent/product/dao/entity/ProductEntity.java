package com.bitmall.agent.product.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** product 表的可写持久化对象，不直接作为接口返回。 */
@TableName("product")
@Getter
@Setter
public class ProductEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("name")
  private String name;

  @TableField("category")
  private String category;

  @TableField("brand")
  private String brand;

  @TableField("summary")
  private String summary;

  @TableField("image_key")
  private String imageKey;

  @TableField("rating")
  private BigDecimal rating;

  @TableField("sales_count")
  private Integer salesCount;

  @TableField("enabled")
  private Boolean enabled;
}
