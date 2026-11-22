package com.bitmall.agent.user.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 用户的收货地址。 结算服务核对地址归属后使用，创建订单时会把 display 生成的文字保存成快照，避免之后地址变化影响历史订单。 */
@Getter
@EqualsAndHashCode
@ToString
public final class AddressDO {
    private final long id;

    /** 数据所属用户编号，由后端上下文传入。 */
    private final long userId;

    private final String receiver;

    private final String phone;

    private final String province;

    private final String city;

    private final String district;

    private final String detail;

    private final boolean defaultAddress;

    @JsonCreator
    public AddressDO(
            @JsonProperty("id") long id,
            @JsonProperty("userId") long userId,
            @JsonProperty("receiver") String receiver,
            @JsonProperty("phone") String phone,
            @JsonProperty("province") String province,
            @JsonProperty("city") String city,
            @JsonProperty("district") String district,
            @JsonProperty("detail") String detail,
            @JsonProperty("defaultAddress") boolean defaultAddress) {
        this.id = id;
        this.userId = userId;
        this.receiver = receiver;
        this.phone = phone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detail = detail;
        this.defaultAddress = defaultAddress;
    }

    /** 把收货人、电话和完整地址拼成展示文字，建单时保存这份快照。 */
    public String display() {
        return receiver + " " + phone + " " + province + city + district + detail;
    }
}
