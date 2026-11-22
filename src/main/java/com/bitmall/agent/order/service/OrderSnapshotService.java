package com.bitmall.agent.order.service;

import com.bitmall.agent.cart.vo.CartItemVO;
import com.bitmall.agent.checkout.service.CheckoutService;
import com.bitmall.agent.checkout.vo.CheckoutQuoteVO;
import com.bitmall.agent.coupon.vo.CouponVO;
import com.bitmall.agent.order.exception.OrderRejectedException;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.bitmall.agent.order.model.PurchaseItem;
import com.bitmall.agent.product.dao.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 购买快照：用户确认时冻结的后端报价（买哪些 SKU、各多少件、单价、地址、优惠、应付金额）。
 *
 * <p>快照保存在 trace 里，之后的建单只认快照，不受用户随后修改购物车的影响。 建单前会按快照里的 SKU 和数量重新试算一次，
 * 价格、地址或应付金额有任何变化都拒绝建单，要求用户重新确认。
 */
@Service
@RequiredArgsConstructor
public class OrderSnapshotService {
  /** 生成购买快照时试算报价；复核时按快照里的商品重新试算。 */
  private final CheckoutService checkout;

  /** 复核时重新读取每个 SKU 的当前价格和库存。 */
  private final ProductRepository products;

  /** 购买快照、结果快照与 JSON 文本互相转换。 */
  private final ObjectMapper json;

  /**
   * 按用户提交的下单参数试算报价，并核对用户在确认页看到的金额和明细没有变化。只读，不扣库存、不发消息。
   *
   * @throws IllegalArgumentException 缺少确认金额，或应付金额/购买明细与确认时不一致
   */
  public CheckoutQuoteVO prepare(long user, CreateOrderCommand command) {
    CheckoutQuoteVO quote = quote(user, command);
    if (command.getExpectedPayAmount() == null) {
      throw new IllegalArgumentException("缺少确认的结算金额");
    }
    if (command.getExpectedPayAmount().compareTo(quote.getPayAmount()) != 0) {
      throw new IllegalArgumentException("结算金额已经变化，请重新确认");
    }
    if (command.getExpectedItems() != null) {
      List<String> confirmedItems = purchaseItemKeys(command.getExpectedItems());
      List<String> quotedItems = quoteItemKeys(quote.getItems());
      if (!confirmedItems.equals(quotedItems)) {
        throw new IllegalArgumentException("购买明细已经变化，请重新确认");
      }
    }
    return quote;
  }

  private CheckoutQuoteVO quote(long user, CreateOrderCommand command) {
    if (command.fromCart()) {
      return checkout.cartCheckoutQuote(user, command.getAddressId(), command.getUserCouponId());
    }
    return checkout.checkoutQuote(
        user,
        command.requiredSkuId(),
        command.getQuantity(),
        command.getAddressId(),
        command.getUserCouponId());
  }

  /**
   * 建单前复核：SKU 和数量固定用快照里的，按当前数据重新试算。
   *
   * @throws OrderRejectedException 商品下架（UNAVAILABLE）、单价变化（PRICE_CHANGED）、试算失败（QUOTE_INVALID）、
   *     地址或应付金额变化（QUOTE_CHANGED）
   */
  public CheckoutQuoteVO refresh(long user, CheckoutQuoteVO snapshot) {
    List<CartItemVO> currentItems = new ArrayList<>();
    for (CartItemVO snapshotItem : snapshot.getItems()) {
      currentItems.add(currentItem(snapshotItem));
    }

    CheckoutQuoteVO current;
    CouponVO coupon = snapshot.getCoupon();
    try {
      current =
          checkout.quoteItems(
              user,
              currentItems,
              snapshot.getAddress().getId(),
              coupon == null ? null : coupon.getUserCouponId(),
              snapshot.getSource());
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new OrderRejectedException("QUOTE_INVALID", ex.getMessage());
    }

    boolean addressChanged = !current.getAddress().equals(snapshot.getAddress());
    boolean payAmountChanged = current.getPayAmount().compareTo(snapshot.getPayAmount()) != 0;
    if (addressChanged || payAmountChanged) {
      throw new OrderRejectedException("QUOTE_CHANGED", "地址或结算金额已经变化，请重新确认");
    }
    return current;
  }

  /** 读取 SKU 的当前信息，单价必须与快照一致；数量、购物车条目编号和勾选状态沿用快照。 */
  private CartItemVO currentItem(CartItemVO snapshotItem) {
    CartItemVO now =
        products
            .findPurchaseItem(snapshotItem.getSkuId(), snapshotItem.getQuantity())
            .orElseThrow(() -> new OrderRejectedException("UNAVAILABLE", "商品已经下架"));
    if (now.getPrice().compareTo(snapshotItem.getPrice()) != 0) {
      throw new OrderRejectedException("PRICE_CHANGED", "商品价格已经变化，请重新确认");
    }
    return new CartItemVO(
        snapshotItem.getId(),
        now.getSkuId(),
        now.getProductId(),
        now.getProductName(),
        now.getSkuSpec(),
        now.getImageKey(),
        now.getPrice(),
        now.getStock(),
        snapshotItem.getQuantity(),
        snapshotItem.isSelected());
  }

  /** 把快照或结果序列化成 JSON 存进 trace。失败时抛异常，让所在事务回滚。 */
  public String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("下单快照序列化失败", ex);
    }
  }

  /** 从 trace 里还原购买快照。 */
  public CheckoutQuoteVO read(String value) {
    try {
      return json.readValue(value, CheckoutQuoteVO.class);
    } catch (Exception ex) {
      throw new IllegalStateException("下单快照损坏", ex);
    }
  }

  /** 计算 SHA-256 摘要，用于校验快照没被改动。它只是完整性摘要，不能当购买编号用。 */
  public String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** 还原成功购买的结果。同一购买编号重试时返回这份结果，保证用户看到的是同一笔订单。 */
  @SuppressWarnings("unchecked")
  public Map<String, Object> result(String value) {
    try {
      return json.readValue(value, Map.class);
    } catch (Exception ex) {
      throw new IllegalStateException("下单结果损坏", ex);
    }
  }

  /**
   * 计算用户提交参数的摘要，与 trace 绑定。
   *
   * <p>同一购买编号重试时只比较这个摘要，不重新报价；摘要不同说明有人想用同一个编号换购买内容，直接拒绝。 摘要内容依次为：来源、SKU、数量、地址、用户券、确认金额、确认明细，用 | 连接。
   */
  public String commandHash(CreateOrderCommand command) {
    String amount =
        command.getExpectedPayAmount() == null
            ? "null"
            : command.getExpectedPayAmount().stripTrailingZeros().toPlainString();
    String items =
        command.getExpectedItems() == null
            ? "null"
            : purchaseItemKeys(command.getExpectedItems()).toString();
    return hash(
        String.join(
            "|",
            String.valueOf(command.getSource()),
            String.valueOf(command.getSkuId()),
            String.valueOf(command.getQuantity()),
            String.valueOf(command.getAddressId()),
            String.valueOf(command.getUserCouponId()),
            amount,
            items));
  }

  /** 明细转成排好序的 "skuId:数量" 列表，便于与报价明细比较（与顺序无关）。 */
  private static List<String> purchaseItemKeys(List<PurchaseItem> items) {
    return items.stream().map(item -> item.getSkuId() + ":" + item.getQuantity()).sorted().toList();
  }

  private static List<String> quoteItemKeys(List<CartItemVO> items) {
    return items.stream().map(item -> item.getSkuId() + ":" + item.getQuantity()).sorted().toList();
  }
}
