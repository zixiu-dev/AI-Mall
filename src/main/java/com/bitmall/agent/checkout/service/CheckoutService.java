package com.bitmall.agent.checkout.service;

import com.bitmall.agent.cart.dao.repository.CartRepository;
import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.coupon.dao.repository.CouponRepository;
import com.bitmall.agent.coupon.vo.CouponVO;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import com.bitmall.agent.user.dao.entity.AddressDO;
import com.bitmall.agent.user.dao.repository.AddressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** 统一计算立即购买和购物车的结算报价。 直接读取数据库中的库存、地址和用户券，核对使用条件后计算商品金额、优惠、运费和实付金额。 */
@Service
public class CheckoutService {
  /** 免基础运费的商品金额门槛，按优惠后的商品金额判断。 */
  private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("99.00");

  /** 未达到免运费门槛时收取的基础运费。 */
  private static final BigDecimal FREIGHT = new BigDecimal("8.00");

  /** 依赖组件：商品和规格的基础数据访问组件。 */
  private final ProductRepository products;

  /** 依赖组件：购物车表的数据访问组件。 */
  private final CartRepository carts;

  /** 依赖组件：收货地址的数据访问组件。 */
  private final AddressRepository addresses;

  /** 依赖组件：优惠券活动和用户券的数据访问组件。 */
  private final CouponRepository coupons;

  /** 由 Spring 创建本组件时注入所需依赖。 这里只保存依赖，具体业务在下面的方法中执行。 */
  public CheckoutService(
      ProductRepository products,
      CartRepository carts,
      AddressRepository addresses,
      CouponRepository coupons) {
    this.products = products;
    this.carts = carts;
    this.addresses = addresses;
    this.coupons = coupons;
  }

  /** 立即购买试算：直接读取所选规格的当前库存和价格，再交给统一报价逻辑。 */
  public CheckoutQuoteVO checkoutQuote(long userId, long skuId, int quantity, Long addressId) {
    return checkoutQuote(userId, skuId, quantity, addressId, null);
  }

  /** 立即购买试算：直接读取所选规格的当前库存和价格，再交给统一报价逻辑。 */
  public CheckoutQuoteVO checkoutQuote(
      long userId, long skuId, int quantity, Long addressId, Long userCouponId) {
    validateQuantity(quantity);
    CartItemVO item =
        products
            .findPurchaseItem(skuId, quantity)
            .orElseThrow(() -> new IllegalArgumentException("商品规格不存在或已经下架"));
    return quote(userId, List.of(item), addressId, userCouponId, "DIRECT");
  }

  /** 购物车试算：只读取已勾选项，没有勾选商品时拒绝。 */
  public CheckoutQuoteVO cartCheckoutQuote(long userId, Long addressId, Long userCouponId) {
    List<CartItemVO> selected =
        carts.listCart(userId).stream().filter(CartItemVO::isSelected).toList();
    if (selected.isEmpty()) throw new IllegalStateException("请先选择需要结算的购物车商品");
    return quote(userId, selected, addressId, userCouponId, "CART");
  }

  /** 逐项核对库存、地址和用户券，使用 BigDecimal 计算商品额、优惠、运费和实付。 */
  private CheckoutQuoteVO quote(
      long userId, List<CartItemVO> items, Long addressId, Long userCouponId, String source) {
    return quoteItems(userId, items, addressId, userCouponId, source);
  }

  /** 异步建单重算固定商品快照，不能重新读取用户后来修改的购物车。 */
  public CheckoutQuoteVO quoteItems(
      long userId, List<CartItemVO> items, Long addressId, Long userCouponId, String source) {
    // 1. 每项都核对数量和数据库库存，不能用展示缓存决定是否允许购买。
    for (CartItemVO item : items) {
      validateQuantity(item.getQuantity());
      if (item.getStock() < item.getQuantity()) {
        throw new IllegalStateException(
            item.getProductName() + " 库存不足，当前仅剩 " + item.getStock() + " 件");
      }
    }
    // 2. 地址从当前用户名下查询，避免把别人的地址用于本单。
    AddressDO address =
        addresses.defaultAddress(userId).orElseThrow(() -> new IllegalStateException("用户没有可用收货地址"));
    if (addressId != null && address.getId() != addressId) {
      throw new IllegalArgumentException("演示用户当前仅配置了默认地址 " + address.getId());
    }
    // 3. 金额用 BigDecimal：先算商品合计，再校验并抵扣用户选中的券，最后算运费。
    BigDecimal goods =
        items.stream()
            .map(CartItemVO::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    CouponVO coupon = null;
    BigDecimal discount = BigDecimal.ZERO.setScale(2);
    // 这里需要的是领取后的 userCouponId，不是活动 couponId；没有选券就按无券方案计算。
    if (userCouponId != null) {
      coupon =
          coupons
              .findUserCoupon(userId, userCouponId)
              .orElseThrow(() -> new IllegalArgumentException("优惠券不存在或不属于当前用户"));
      LocalDateTime now = LocalDateTime.now();
      if (!"AVAILABLE".equals(coupon.getUserStatus())
          || now.isBefore(coupon.getStartAt())
          || !now.isBefore(coupon.getEndAt())) {
        throw new IllegalStateException("优惠券当前不可用");
      }
      if (goods.compareTo(coupon.getThresholdAmount()) < 0) {
        throw new IllegalStateException("订单金额未达到优惠券门槛 ¥" + coupon.getThresholdAmount());
      }
      discount = coupon.getDiscountAmount().min(goods).setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal freight =
        goods.subtract(discount).compareTo(FREE_SHIPPING_THRESHOLD) >= 0
            ? BigDecimal.ZERO.setScale(2)
            : FREIGHT;
    BigDecimal pay = goods.subtract(discount).add(freight).setScale(2, RoundingMode.HALF_UP);
    return new CheckoutQuoteVO(
        source, items, goods, discount, freight, pay, address, coupon, LocalDate.now().plusDays(2));
  }

  /** 限制单项数量，防止非法或过大数量进入报价。 */
  private static void validateQuantity(int quantity) {
    if (quantity < 1 || quantity > 20) throw new IllegalArgumentException("购买数量必须在 1 到 20 之间");
  }
}
