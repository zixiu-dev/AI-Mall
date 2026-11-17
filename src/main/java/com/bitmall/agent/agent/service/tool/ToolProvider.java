package com.bitmall.agent.agent.service.tool;

import java.util.List;

/** 按功能提供工具定义的接口。 商品、订单、优惠券等模块各自提供工具列表，由 ToolRegistry 聚合，避免所有定义挤在一个构造方法里。 */
public interface ToolProvider {
    /** 返回当前功能模块提供的全部工具定义，由统一目录聚合。 */
    List<RegisteredTool> tools();
}
