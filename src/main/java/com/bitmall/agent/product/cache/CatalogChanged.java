package com.bitmall.agent.product.cache;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/** 商品目录或库存已经发生业务变更的通知。 订单服务在事务内发布，缓存监听器等事务真正提交后才失效缓存，避免回滚操作也清空缓存。 */
public final class CatalogChanged {
    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public CatalogChanged() {}

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return true;
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash();
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "CatalogChanged[]";
    }
}
