package com.bitmall.agent.product.dao.entity;

import com.baomidou.mybatisplus.annotation.*;

/** product 表的可写持久化对象，不直接作为接口返回。 */
@TableName("product")
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
  private java.math.BigDecimal rating;

  @TableField("sales_count")
  private Integer salesCount;

  @TableField("enabled")
  private Boolean enabled;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getImageKey() {
    return imageKey;
  }

  public void setImageKey(String imageKey) {
    this.imageKey = imageKey;
  }

  public java.math.BigDecimal getRating() {
    return rating;
  }

  public void setRating(java.math.BigDecimal rating) {
    this.rating = rating;
  }

  public Integer getSalesCount() {
    return salesCount;
  }

  public void setSalesCount(Integer salesCount) {
    this.salesCount = salesCount;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
}
