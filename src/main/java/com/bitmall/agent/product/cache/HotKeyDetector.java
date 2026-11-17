package com.bitmall.agent.product.cache;

import java.util.Map;

/** 热点识别接口。 缓存只依赖这个接口判断某个 key 是否热门，正式实现使用京东客户端，测试可以替换为可控制的假实现。 */
public interface HotKeyDetector {
    /** 上报一次 key 访问，并查询服务端推送的热点标记。 */
    boolean isHot(String key);

    /** 返回热点探测器的只读运行状态。 */
    Map<String, Object> status();
}
