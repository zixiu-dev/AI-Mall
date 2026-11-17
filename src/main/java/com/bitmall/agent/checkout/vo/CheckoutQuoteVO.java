package com.bitmall.agent.checkout.vo;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.coupon.vo.CouponVO;
import com.bitmall.agent.user.dao.entity.AddressDO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次结算试算的完整结果。 包含商品、优惠、运费、实付金额、地址和选中的券；试算不会建单，toMap 只是整理成页面和工具约定的 JSON 字段。 */
public final class CheckoutQuoteVO {
    /** 购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
    private final String source;

    /** 本次查询或结算包含的数据项。 */
    private final List<CartItemVO> items;

    /** 商品金额合计，尚未扣优惠和加运费。 */
    private final BigDecimal goodsAmount;

    /** 本次结算实际抵扣的优惠金额。 */
    private final BigDecimal discountAmount;

    /** 本次结算的运费。 */
    private final BigDecimal freightAmount;

    /** 最终应付金额，由后端统一核算。 */
    private final BigDecimal payAmount;

    /** 收货地址或下单时保存的地址快照。 */
    private final AddressDO address;

    /** 本次结算选中的用户优惠券，没有使用时为空。 */
    private final CouponVO coupon;

    /** 展示给用户的预计送达日期。 */
    private final LocalDate estimatedDelivery;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public CheckoutQuoteVO(
            @JsonProperty("source") String source,
            @JsonProperty("items") List<CartItemVO> items,
            @JsonProperty("goodsAmount") BigDecimal goodsAmount,
            @JsonProperty("discountAmount") BigDecimal discountAmount,
            @JsonProperty("freightAmount") BigDecimal freightAmount,
            @JsonProperty("payAmount") BigDecimal payAmount,
            @JsonProperty("address") AddressDO address,
            @JsonProperty("coupon") CouponVO coupon,
            @JsonProperty("estimatedDelivery") LocalDate estimatedDelivery) {
        items = List.copyOf(items);
        this.source = source;
        this.items = items;
        this.goodsAmount = goodsAmount;
        this.discountAmount = discountAmount;
        this.freightAmount = freightAmount;
        this.payAmount = payAmount;
        this.address = address;
        this.coupon = coupon;
        this.estimatedDelivery = estimatedDelivery;
    }

    /** 获取购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
    public String getSource() {
        return source;
    }

    /** 获取本次查询或结算包含的数据项。 */
    public List<CartItemVO> getItems() {
        return items;
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

    /** 获取收货地址或下单时保存的地址快照。 */
    public AddressDO getAddress() {
        return address;
    }

    /** 获取本次结算选中的用户优惠券，没有使用时为空。 */
    public CouponVO getCoupon() {
        return coupon;
    }

    /** 获取展示给用户的预计送达日期。 */
    public LocalDate getEstimatedDelivery() {
        return estimatedDelivery;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CheckoutQuoteVO that = (CheckoutQuoteVO) other;
        return Objects.equals(source, that.source)
                && Objects.equals(items, that.items)
                && Objects.equals(goodsAmount, that.goodsAmount)
                && Objects.equals(discountAmount, that.discountAmount)
                && Objects.equals(freightAmount, that.freightAmount)
                && Objects.equals(payAmount, that.payAmount)
                && Objects.equals(address, that.address)
                && Objects.equals(coupon, that.coupon)
                && Objects.equals(estimatedDelivery, that.estimatedDelivery);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                source,
                items,
                goodsAmount,
                discountAmount,
                freightAmount,
                payAmount,
                address,
                coupon,
                estimatedDelivery);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CheckoutQuoteVO["
                + "source="
                + source
                + ", items="
                + items
                + ", goodsAmount="
                + goodsAmount
                + ", discountAmount="
                + discountAmount
                + ", freightAmount="
                + freightAmount
                + ", payAmount="
                + payAmount
                + ", address="
                + address
                + ", coupon="
                + coupon
                + ", estimatedDelivery="
                + estimatedDelivery
                + "]";
    }

    /** 按现有页面和工具协议整理报价；只有使用优惠券或只有一项商品时才追加对应字段。 */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("items", items);
        result.put("goodsAmount", goodsAmount);
        result.put("discountAmount", discountAmount);
        result.put("freightAmount", freightAmount);
        result.put("payAmount", payAmount);
        result.put("addressId", address.getId());
        result.put("address", address.display());
        result.put("estimatedDelivery", estimatedDelivery.toString());
        if (coupon != null) {
            result.put("userCouponId", coupon.getUserCouponId());
            result.put("couponId", coupon.getId());
            result.put("couponName", coupon.getName());
        }
        if (items.size() == 1) {
            CartItemVO item = items.get(0);
            result.put("skuId", item.getSkuId());
            result.put("spec", item.getSkuSpec());
            result.put("quantity", item.getQuantity());
            result.put("stock", item.getStock());
        }
        return result;
    }
}
