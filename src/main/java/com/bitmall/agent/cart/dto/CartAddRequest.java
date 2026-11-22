package com.bitmall.agent.cart.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 手动加入购物车的请求参数。 用户编号和数量可以省略，演示环境分别默认使用 10001 和 1，库存及数量上限由 CartService 检查。 */
@Getter
@EqualsAndHashCode
@ToString
public final class CartAddRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 具体商品规格的编号，不等于商品编号。 */
    private final long skuId;

    private final Integer quantity;

    @JsonCreator
    public CartAddRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("skuId") long skuId,
            @JsonProperty("quantity") Integer quantity) {
        this.userId = userId;
        this.skuId = skuId;
        this.quantity = quantity;
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
