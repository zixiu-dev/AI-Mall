package com.bitmall.agent.user.dao.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** 用户的收货地址。 结算服务核对地址归属后使用，创建订单时会把 display 生成的文字保存成快照，避免之后地址变化影响历史订单。 */
public final class AddressDO {
    /** 这条数据的唯一编号。 */
    private final long id;

    /** 数据所属用户编号，由后端上下文传入。 */
    private final long userId;

    /** 收货人姓名。 */
    private final String receiver;

    /** 收货人联系电话。 */
    private final String phone;

    /** 收货省份。 */
    private final String province;

    /** 收货城市。 */
    private final String city;

    /** 收货区县。 */
    private final String district;

    /** 详细收货地址。 */
    private final String detail;

    /** 是否为默认收货地址。 */
    private final boolean defaultAddress;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
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

    /** 获取这条数据的唯一编号。 */
    public long getId() {
        return id;
    }

    /** 获取数据所属用户编号，由后端上下文传入。 */
    public long getUserId() {
        return userId;
    }

    /** 获取收货人姓名。 */
    public String getReceiver() {
        return receiver;
    }

    /** 获取收货人联系电话。 */
    public String getPhone() {
        return phone;
    }

    /** 获取收货省份。 */
    public String getProvince() {
        return province;
    }

    /** 获取收货城市。 */
    public String getCity() {
        return city;
    }

    /** 获取收货区县。 */
    public String getDistrict() {
        return district;
    }

    /** 获取详细收货地址。 */
    public String getDetail() {
        return detail;
    }

    /** 获取是否为默认收货地址。 */
    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AddressDO that = (AddressDO) other;
        return Objects.equals(id, that.id)
                && Objects.equals(userId, that.userId)
                && Objects.equals(receiver, that.receiver)
                && Objects.equals(phone, that.phone)
                && Objects.equals(province, that.province)
                && Objects.equals(city, that.city)
                && Objects.equals(district, that.district)
                && Objects.equals(detail, that.detail)
                && Objects.equals(defaultAddress, that.defaultAddress);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(
                id, userId, receiver, phone, province, city, district, detail, defaultAddress);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "AddressDO["
                + "id="
                + id
                + ", userId="
                + userId
                + ", receiver="
                + receiver
                + ", phone="
                + phone
                + ", province="
                + province
                + ", city="
                + city
                + ", district="
                + district
                + ", detail="
                + detail
                + ", defaultAddress="
                + defaultAddress
                + "]";
    }

    /** 把收货人、电话和完整地址拼成展示文字，建单时保存这份快照。 */
    public String display() {
        return receiver + " " + phone + " " + province + city + district + detail;
    }
}
