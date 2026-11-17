package com.bitmall.agent.order.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 面向页面和 Agent 的订单查询结果。 由 OrderMapper 组合订单主信息、商品明细和物流轨迹；查看之前会按用户编号限制查询范围。 */
public final class OrderDetailVO {
  /** 订单编号。 */
  private final String id;

  /** 数据所属用户编号，由后端上下文传入。 */
  private final long userId;

  /** 当前业务状态，由服务端维护。 */
  private final String status;

  /** 商品金额合计，尚未扣优惠和加运费。 */
  private final BigDecimal goodsAmount;

  /** 本次结算实际抵扣的优惠金额。 */
  private final BigDecimal discountAmount;

  /** 本次结算的运费。 */
  private final BigDecimal freightAmount;

  /** 最终应付金额，由后端统一核算。 */
  private final BigDecimal payAmount;

  /** 下单时记录的优惠券名称。 */
  private final String couponName;

  /** 下单时保存的收货地址文字快照。 */
  private final String address;

  /** 数据创建时间。 */
  private final LocalDateTime createdAt;

  /** 待支付有效期，历史已支付订单可能为空。 */
  private final LocalDateTime paymentExpiresAt;

  /** 本次查询或结算包含的数据项。 */
  private final List<OrderItemVO> items;

  /** 订单的物流轨迹。 */
  private final List<LogisticsEventVO> logistics;

  /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
  @JsonCreator
  public OrderDetailVO(
      @JsonProperty("id") String id,
      @JsonProperty("userId") long userId,
      @JsonProperty("status") String status,
      @JsonProperty("goodsAmount") BigDecimal goodsAmount,
      @JsonProperty("discountAmount") BigDecimal discountAmount,
      @JsonProperty("freightAmount") BigDecimal freightAmount,
      @JsonProperty("payAmount") BigDecimal payAmount,
      @JsonProperty("couponName") String couponName,
      @JsonProperty("address") String address,
      @JsonProperty("createdAt") LocalDateTime createdAt,
      @JsonProperty("paymentExpiresAt") LocalDateTime paymentExpiresAt,
      @JsonProperty("items") List<OrderItemVO> items,
      @JsonProperty("logistics") List<LogisticsEventVO> logistics) {
    this.id = id;
    this.userId = userId;
    this.status = status;
    this.goodsAmount = goodsAmount;
    this.discountAmount = discountAmount;
    this.freightAmount = freightAmount;
    this.payAmount = payAmount;
    this.couponName = couponName;
    this.address = address;
    this.createdAt = createdAt;
    this.paymentExpiresAt = paymentExpiresAt;
    this.items = items;
    this.logistics = logistics;
  }

  /** 获取订单编号。 */
  public String getId() {
    return id;
  }

  /** 获取数据所属用户编号，由后端上下文传入。 */
  public long getUserId() {
    return userId;
  }

  /** 获取当前业务状态，由服务端维护。 */
  public String getStatus() {
    return status;
  }

  /** 获取商品金额合计，尚未扣优惠和加运费。 */
  public BigDecimal getGoodsAmount() {
    return goodsAmount;
  }

  /** 获取本次结算实际抵扣的优惠金额。 */
  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  /** 获取本次结算的运费。 */
  public BigDecimal getFreightAmount() {
    return freightAmount;
  }

  /** 获取最终应付金额，由后端统一核算。 */
  public BigDecimal getPayAmount() {
    return payAmount;
  }

  /** 获取下单时记录的优惠券名称。 */
  public String getCouponName() {
    return couponName;
  }

  /** 获取下单时保存的收货地址文字快照。 */
  public String getAddress() {
    return address;
  }

  /** 获取数据创建时间。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 获取支付截止时间。 */
  public LocalDateTime getPaymentExpiresAt() {
    return paymentExpiresAt;
  }

  /** 获取本次查询或结算包含的数据项。 */
  public List<OrderItemVO> getItems() {
    return items;
  }

  /** 获取订单的物流轨迹。 */
  public List<LogisticsEventVO> getLogistics() {
    return logistics;
  }

  /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    OrderDetailVO that = (OrderDetailVO) other;
    return Objects.equals(id, that.id)
        && Objects.equals(userId, that.userId)
        && Objects.equals(status, that.status)
        && Objects.equals(goodsAmount, that.goodsAmount)
        && Objects.equals(discountAmount, that.discountAmount)
        && Objects.equals(freightAmount, that.freightAmount)
        && Objects.equals(payAmount, that.payAmount)
        && Objects.equals(couponName, that.couponName)
        && Objects.equals(address, that.address)
        && Objects.equals(createdAt, that.createdAt)
        && Objects.equals(paymentExpiresAt, that.paymentExpiresAt)
        && Objects.equals(items, that.items)
        && Objects.equals(logistics, that.logistics);
  }

  /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        userId,
        status,
        goodsAmount,
        discountAmount,
        freightAmount,
        payAmount,
        couponName,
        address,
        createdAt,
        items,
        logistics);
  }

  /** 返回字段摘要，供日志和本地调试查看。 */
  @Override
  public String toString() {
    return "OrderDetailVO["
        + "id="
        + id
        + ", userId="
        + userId
        + ", status="
        + status
        + ", goodsAmount="
        + goodsAmount
        + ", discountAmount="
        + discountAmount
        + ", freightAmount="
        + freightAmount
        + ", payAmount="
        + payAmount
        + ", couponName="
        + couponName
        + ", address="
        + address
        + ", createdAt="
        + createdAt
        + ", items="
        + items
        + ", logistics="
        + logistics
        + "]";
  }
}
