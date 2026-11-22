package com.bitmall.agent.checkout.dto;

import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.model.PurchaseItem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Objects;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;

/** 页面进行结算试算或建单时提交的参数。 立即购买需要 SKU，购物车结算使用已勾选商品；真正建单还需要用户确认过的 expectedPayAmount。 */
public final class CheckoutRequest {
  /** 数据所属用户编号，由后端上下文传入。 */
  @Getter
  @Positive private final Long userId;

  /** 购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
  @Pattern(regexp = "DIRECT|CART", message = "购买来源必须为 DIRECT 或 CART")
  @Getter
  private final String source;

  /** 具体商品规格的编号，不等于商品编号。 */
  @Getter
  @Positive private final Long skuId;

  @Min(1)
  @Max(20)
  @Getter
  private final Integer quantity;

  /** 用户选择的收货地址编号，省略时使用默认地址。 */
  @Getter
  @Positive private final Long addressId;

  /** 用户领取后生成的券编号，结算时使用它。 */
  @Getter
  @Positive private final Long userCouponId;

  /** 用户确认时看到的应付金额，建单前需要重新核对。 */
  @DecimalMin("0.00")
  @Getter
  private final BigDecimal expectedPayAmount;

  /** 用户看到并确认的购物明细，轻量发号后的购物车提交必须提供。 */
  @Valid
  @Size(max = 100)
  private final List<PurchaseItem> expectedItems;

  public CheckoutRequest(
      Long userId,
      String source,
      Long skuId,
      Integer quantity,
      Long addressId,
      Long userCouponId,
      BigDecimal expectedPayAmount) {
    this(userId, source, skuId, quantity, addressId, userCouponId, expectedPayAmount, null);
  }

  @JsonCreator
  public CheckoutRequest(
      @JsonProperty("userId") Long userId,
      @JsonProperty("source") String source,
      @JsonProperty("skuId") Long skuId,
      @JsonProperty("quantity") Integer quantity,
      @JsonProperty("addressId") Long addressId,
      @JsonProperty("userCouponId") Long userCouponId,
      @JsonProperty("expectedPayAmount") BigDecimal expectedPayAmount,
      @JsonProperty("expectedItems") List<PurchaseItem> expectedItems) {
    this.expectedItems = expectedItems == null ? null : List.copyOf(expectedItems);
    this.userId = userId;
    this.source = source;
    this.skuId = skuId;
    this.quantity = quantity;
    this.addressId = addressId;
    this.userCouponId = userCouponId;
    this.expectedPayAmount = expectedPayAmount;
  }

  /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    CheckoutRequest that = (CheckoutRequest) other;
    return Objects.equals(userId, that.userId)
        && Objects.equals(source, that.source)
        && Objects.equals(skuId, that.skuId)
        && Objects.equals(quantity, that.quantity)
        && Objects.equals(addressId, that.addressId)
        && Objects.equals(userCouponId, that.userCouponId)
        && Objects.equals(expectedPayAmount, that.expectedPayAmount);
  }

  /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
  @Override
  public int hashCode() {
    return Objects.hash(
        userId, source, skuId, quantity, addressId, userCouponId, expectedPayAmount);
  }

  /** 返回字段摘要，供日志和本地调试查看。 */
  @Override
  public String toString() {
    return "CheckoutRequest["
        + "userId="
        + userId
        + ", source="
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

  /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
  public long resolvedUserId() {
    return userId == null ? 10001L : userId;
  }

  /** 没有填写来源时按立即购买 DIRECT 处理。 */
  public String resolvedSource() {
    return source == null || source.isBlank() ? "DIRECT" : source;
  }

  /** 没有提供数量时使用 1，后续业务服务仍会检查合法范围和库存。 */
  public int resolvedQuantity() {
    return quantity == null ? 1 : quantity;
  }

  /** 立即购买必须提供具体 SKU 编号，缺少时明确拒绝。 */
  public long requiredSkuId() {
    if (skuId == null) {
      throw new IllegalArgumentException("直接购买缺少 skuId");
    }
    return skuId;
  }

  /** 把页面请求转成普通下单命令，业务服务因此不需要读取 HTTP 或 JSON 对象。 */
  public CreateOrderCommand toCommand() {
    return new CreateOrderCommand(
        resolvedSource(),
        skuId,
        resolvedQuantity(),
        addressId,
        userCouponId,
        expectedPayAmount,
        expectedItems);
  }
}
