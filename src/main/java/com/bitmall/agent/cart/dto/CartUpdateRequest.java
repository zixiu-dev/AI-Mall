package com.bitmall.agent.cart.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 修改购物车某一项时的请求参数。 可以调整数量和勾选状态；selected 为空表示保持当前勾选状态。 */
@Getter
@EqualsAndHashCode
@ToString
public final class CartUpdateRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    private final Integer quantity;

    /** 购物车中是否勾选此项参与结算。 */
    private final Boolean selected;

    @JsonCreator
    public CartUpdateRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("selected") Boolean selected) {
        this.userId = userId;
        this.quantity = quantity;
        this.selected = selected;
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
