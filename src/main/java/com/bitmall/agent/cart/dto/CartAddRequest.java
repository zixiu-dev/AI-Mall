package com.bitmall.agent.cart.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 手动加入购物车的请求参数。 用户编号和数量可以省略，演示环境分别默认使用 10001 和 1，库存及数量上限由 CartService 检查。 */
public final class CartAddRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    /** 购买数量。 */
    private final Integer quantity;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public CartAddRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("skuId") long skuId,
            @JsonProperty("quantity") Integer quantity) {
        this.userId = userId;
        this.skuId = skuId;
        this.quantity = quantity;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public Long getUserId() {
        return userId;
    }

    /** 获取具体商品规格的编号，不等于商品编号。 */
    public long getSkuId() {
        return skuId;
    }

    /** 获取购买数量。 */
    public Integer getQuantity() {
        return quantity;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CartAddRequest that = (CartAddRequest) other;
        return Objects.equals(userId, that.userId)
                && Objects.equals(skuId, that.skuId)
                && Objects.equals(quantity, that.quantity);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(userId, skuId, quantity);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CartAddRequest["
                + "userId="
                + userId
                + ", skuId="
                + skuId
                + ", quantity="
                + quantity
                + "]";
    }

    /** 演示环境没有提交用户编号时使用 10001；正式身份接入应在接口入口统一替换。 */
    public long resolvedUserId() {
        return userId == null ? 10001L : userId;
    }

    /** 没有提供数量时使用 1，后续业务服务仍会检查合法范围和库存。 */
    public int resolvedQuantity() {
        return quantity == null ? 1 : quantity;
    }
}
