package com.bitmall.agent.common.idempotent;

import java.lang.annotation.*;

/** 声明防重场景和业务键。接口使用 Redisson 锁，MQ 使用 Redis 消费状态；最终业务仍由数据库 CAS 和唯一约束保护。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /** 业务场景，受理和建单必须使用不同场景。 */
    String scene();

    /** SpEL 业务键；为空时按方法参数的规范化 JSON 摘要生成。 */
    String key() default "";

    /** 是否为 MQ 消费场景。 */
    boolean mq() default false;
}
