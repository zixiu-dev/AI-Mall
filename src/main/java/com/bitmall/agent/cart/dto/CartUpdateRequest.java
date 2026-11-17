package com.bitmall.agent.cart.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 修改购物车某一项时的请求参数。 可以调整数量和勾选状态；selected 为空表示保持当前勾选状态。 */
public final class CartUpdateRequest {
    /** 数据所属用户编号，由后端上下文传入。 */
    private final Long userId;

    /** 购买数量。 */
    private final Integer quantity;

    /** 购物车中是否勾选此项参与结算。 */
    private final Boolean selected;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public CartUpdateRequest(
            @JsonProperty("userId") Long userId,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("selected") Boolean selected) {
        this.userId = userId;
        this.quantity = quantity;
        this.selected = selected;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public Long getUserId() {
        return userId;
    }

    /** 获取购买数量。 */
    public Integer getQuantity() {
        return quantity;
    }

    /** 获取购物车中是否勾选此项参与结算。 */
    public Boolean getSelected() {
        return selected;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CartUpdateRequest that = (CartUpdateRequest) other;
        return Objects.equals(userId, that.userId)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(selected, that.selected);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(userId, quantity, selected);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CartUpdateRequest["
                + "userId="
                + userId
                + ", quantity="
                + quantity
                + ", selected="
                + selected
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
