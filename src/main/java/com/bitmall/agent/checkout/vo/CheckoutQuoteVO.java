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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 一次结算试算的完整结果。 包含商品、优惠、运费、实付金额、地址和选中的券；试算不会建单，toMap 只是整理成页面和工具约定的 JSON 字段。 */
@Getter
@EqualsAndHashCode
@ToString
public final class CheckoutQuoteVO {
    /** 购买来源：DIRECT 表示立即购买，CART 表示购物车结算。 */
    private final String source;

    private final List<CartItemVO> items;

    /** 商品金额合计，尚未扣优惠和加运费。 */
    private final BigDecimal goodsAmount;

    /** 本次结算实际抵扣的优惠金额。 */
    private final BigDecimal discountAmount;

    private final BigDecimal freightAmount;

    /** 最终应付金额，由后端统一核算。 */
    private final BigDecimal payAmount;

    /** 收货地址或下单时保存的地址快照。 */
    private final AddressDO address;

    /** 本次结算选中的用户优惠券，没有使用时为空。 */
    private final CouponVO coupon;

    private final LocalDate estimatedDelivery;

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
