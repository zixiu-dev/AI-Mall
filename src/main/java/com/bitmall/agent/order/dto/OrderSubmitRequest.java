package com.bitmall.agent.order.dto;

import com.bitmall.agent.checkout.dto.CheckoutRequest;
import com.bitmall.agent.order.model.CreateOrderCommand;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** 正式确认下单的参数。首次提交带购买内容，后端绑定 trace 后校验后续重试的一致性。 */
public final class OrderSubmitRequest {
  /** 演示身份编号，正式身份应从登录态取得。 */
  @Positive private final Long userId;

  /** 前一步由后端生成的下单编号。 */
  @NotBlank
  @Pattern(
      regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
      message = "traceId 格式不正确")
  private final String traceId;

  /** 新发号入口不保存购买内容，所以首次提交携带经过参数校验的购买参数。旧已绑定凭证可省略。 */
  @jakarta.validation.Valid private final CheckoutRequest purchase;

  /** 显式保存全部字段，读取时使用下方 Getter。 */
  @JsonCreator
  public OrderSubmitRequest(
      @JsonProperty("userId") Long userId,
      @JsonProperty("traceId") String traceId,
      @JsonProperty("purchase") CheckoutRequest purchase) {
    this.userId = userId;
    this.traceId = traceId;
    this.purchase = purchase;
  }

  public CreateOrderCommand purchaseCommand() {
    return purchase == null ? null : purchase.toCommand();
  }

  /** 获取演示身份编号，正式身份应从登录态取得。 */
  public Long getUserId() {
    return userId;
  }

  /** 获取前一步由后端生成的下单编号。 */
  public String getTraceId() {
    return traceId;
  }

  /** 缺省使用项目演示用户；不能作为生产认证方案。 */
  public long resolvedUserId() {
    return userId == null ? 10001L : userId;
  }
}
