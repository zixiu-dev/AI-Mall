package com.bitmall.agent.order.service;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 冻结后端报价，并在建单时重算同一批 SKU。参数快照不依赖用户后续的购物车操作。 */
@Service
public class OrderSnapshotService {
  /** 共用金额、地址和优惠券规则。 */
  private final CheckoutService checkout;

  /** 从数据库重读单价与库存。 */
  private final ProductRepository products;

  /** 快照 JSON 转换器。 */
  private final ObjectMapper json;

  /** 注入结算、商品和 JSON 组件。 */
  public OrderSnapshotService(
      CheckoutService checkout, ProductRepository products, ObjectMapper json) {
    this.checkout = checkout;
    this.products = products;
    this.json = json;
  }

  /** 确认前核算实际内容，不扣库存或发消息。 */
  public CheckoutQuoteVO prepare(long user, CreateOrderCommand command) {
    CheckoutQuoteVO quote =
        command.fromCart()
            ? checkout.cartCheckoutQuote(user, command.getAddressId(), command.getUserCouponId())
            : checkout.checkoutQuote(
                user,
                command.requiredSkuId(),
                command.getQuantity(),
                command.getAddressId(),
                command.getUserCouponId());
    if (command.getExpectedPayAmount() == null) throw new IllegalArgumentException("缺少确认的结算金额");
    if (command.getExpectedPayAmount().compareTo(quote.getPayAmount()) != 0)
      throw new IllegalArgumentException("结算金额已经变化，请重新确认");
    if (command.getExpectedItems() != null) {
      var expected =
          command.getExpectedItems().stream()
              .map(i -> i.getSkuId() + ":" + i.getQuantity())
              .sorted()
              .toList();
      var actual =
          quote.getItems().stream()
              .map(i -> i.getSkuId() + ":" + i.getQuantity())
              .sorted()
              .toList();
      if (!expected.equals(actual)) throw new IllegalArgumentException("购买明细已经变化，请重新确认");
    }
    return quote;
  }

  /** 固定 SKU 与数量重算，单价、地址或实付变化均要求重新确认。 */
  public CheckoutQuoteVO refresh(long user, CheckoutQuoteVO snapshot) {
    var items = new ArrayList<CartItemVO>();
    for (CartItemVO old : snapshot.getItems()) {
      CartItemVO now =
          products
              .findPurchaseItem(old.getSkuId(), old.getQuantity())
              .orElseThrow(() -> new OrderRejectedException("UNAVAILABLE", "商品已经下架"));
      if (now.getPrice().compareTo(old.getPrice()) != 0)
        throw new OrderRejectedException("PRICE_CHANGED", "商品价格已经变化，请重新确认");
      items.add(
          new CartItemVO(
              old.getId(),
              now.getSkuId(),
              now.getProductId(),
              now.getProductName(),
              now.getSkuSpec(),
              now.getImageKey(),
              now.getPrice(),
              now.getStock(),
              old.getQuantity(),
              old.isSelected()));
    }
    CheckoutQuoteVO current;
    try {
      current =
          checkout.quoteItems(
              user,
              items,
              snapshot.getAddress().getId(),
              snapshot.getCoupon() == null ? null : snapshot.getCoupon().getUserCouponId(),
              snapshot.getSource());
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new OrderRejectedException("QUOTE_INVALID", ex.getMessage());
    }
    if (!current.getAddress().equals(snapshot.getAddress())
        || current.getPayAmount().compareTo(snapshot.getPayAmount()) != 0)
      throw new OrderRejectedException("QUOTE_CHANGED", "地址或结算金额已经变化，请重新确认");
    return current;
  }

  /** 显式保存快照或回执，序列化失败必须回滚事务。 */
  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("下单快照序列化失败", ex);
    }
  }

  /** 恢复后端保存的普通结算对象。 */
  public CheckoutQuoteVO read(String value) {
    try {
      return json.readValue(value, CheckoutQuoteVO.class);
    } catch (Exception ex) {
      throw new IllegalStateException("下单快照损坏", ex);
    }
  }

  /** 计算快照完整性摘要，不把摘要当作购买编号。 */
  public String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 恢复成功结果，用于顺序重试返回同一笔订单。 */
  @SuppressWarnings("unchecked")
  public Map<String, Object> result(String value) {
    try {
      return json.readValue(value, Map.class);
    } catch (Exception ex) {
      throw new IllegalStateException("下单结果损坏", ex);
    }
  }

  /** 绑定用户确认的输入，重试时不重新报价，也不能拿同一个编号换购买内容。 */
  public String commandHash(CreateOrderCommand command) {
    String amount =
        command.getExpectedPayAmount() == null
            ? "null"
            : command.getExpectedPayAmount().stripTrailingZeros().toPlainString();
    return hash(
        command.getSource()
            + "|"
            + command.getSkuId()
            + "|"
            + command.getQuantity()
            + "|"
            + command.getAddressId()
            + "|"
            + command.getUserCouponId()
            + "|"
            + amount
            + "|"
            + (command.getExpectedItems() == null
                ? "null"
                : command.getExpectedItems().stream()
                    .map(i -> i.getSkuId() + ":" + i.getQuantity())
                    .sorted()
                    .toList()));
  }
}
