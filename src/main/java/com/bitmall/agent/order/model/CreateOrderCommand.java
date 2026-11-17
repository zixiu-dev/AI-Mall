package com.bitmall.agent.order.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** 传给订单服务的明确下单参数。 商城接口和 Agent 写工具都转换成这个类；expectedPayAmount 是用户确认过的金额，执行前会重新试算并比较。 */
public final class CreateOrderCommand {
  /** 购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
  private final String source;

  /** 具体商品规格的编号，不等于商品编号。 */
  private final Long skuId;

  /** 购买数量。 */
  private final int quantity;

  /** 用户选择的收货地址编号，省略时使用默认地址。 */
  private final Long addressId;

  /** 用户领取后生成的券编号，结算时使用它。 */
  private final Long userCouponId;

  /** 用户确认时看到的应付金额，建单前需要重新核对。 */
  private final BigDecimal expectedPayAmount;

  /** 页面已经确认的明细；旧内部入口已有快照，可以不传。 */
  private final java.util.List<PurchaseItem> expectedItems;

  public CreateOrderCommand(
      String source,
      Long skuId,
      int quantity,
      Long addressId,
      Long userCouponId,
      BigDecimal expectedPayAmount) {
    this(source, skuId, quantity, addressId, userCouponId, expectedPayAmount, null);
  }

  public java.util.List<PurchaseItem> getExpectedItems() {
    return expectedItems;
  }

  /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
  @JsonCreator
  public CreateOrderCommand(
      @JsonProperty("source") String source,
      @JsonProperty("skuId") Long skuId,
      @JsonProperty("quantity") int quantity,
      @JsonProperty("addressId") Long addressId,
      @JsonProperty("userCouponId") Long userCouponId,
      @JsonProperty("expectedPayAmount") BigDecimal expectedPayAmount,
      @JsonProperty("expectedItems") java.util.List<PurchaseItem> expectedItems) {
    this.expectedItems = expectedItems == null ? null : java.util.List.copyOf(expectedItems);
    source = source == null || source.isBlank() ? "DIRECT" : source.toUpperCase(Locale.ROOT);
    this.source = source;
    this.skuId = skuId;
    this.quantity = quantity;
    this.addressId = addressId;
    this.userCouponId = userCouponId;
    this.expectedPayAmount = expectedPayAmount;
  }

  /** 获取购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
  public String getSource() {
    return source;
  }

  /** 获取具体商品规格的编号，不等于商品编号。 */
  public Long getSkuId() {
    return skuId;
  }

  /** 获取购买数量。 */
  public int getQuantity() {
    return quantity;
  }

  /** 获取用户选择的收货地址编号，省略时使用默认地址。 */
  public Long getAddressId() {
    return addressId;
  }

  /** 获取用户领取后生成的券编号，结算时使用它。 */
  public Long getUserCouponId() {
    return userCouponId;
  }

  /** 获取用户确认时看到的应付金额，建单前需要重新核对。 */
  public BigDecimal getExpectedPayAmount() {
    return expectedPayAmount;
  }

  /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    CreateOrderCommand that = (CreateOrderCommand) other;
    return Objects.equals(source, that.source)
        && Objects.equals(skuId, that.skuId)
        && Objects.equals(quantity, that.quantity)
        && Objects.equals(addressId, that.addressId)
        && Objects.equals(userCouponId, that.userCouponId)
        && Objects.equals(expectedPayAmount, that.expectedPayAmount);
  }

  /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
  @Override
  public int hashCode() {
    return Objects.hash(source, skuId, quantity, addressId, userCouponId, expectedPayAmount);
  }

  /** 返回字段摘要，供日志和本地调试查看。 */
  @Override
  public String toString() {
    return "CreateOrderCommand["
        + "source="
        + source
        + ", skuId="
        + skuId
        + ", quantity="
        + quantity
        + ", addressId="
        + addressId
        + ", userCouponId="
        + userCouponId
        + ", expectedPayAmount="
        + expectedPayAmount
        + "]";
  }

  /** 判断这笔订单是否来源于购物车。 */
  public boolean fromCart() {
    return "CART".equals(source);
  }

  /** 立即购买必须明确规格编号；缺失时直接拒绝，不能由后端猜测。 */
  public long requiredSkuId() {
    if (skuId == null) throw new IllegalArgumentException("缺少参数 skuId");
    return skuId;
  }
}
