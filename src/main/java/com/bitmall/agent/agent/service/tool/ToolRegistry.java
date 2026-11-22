package com.bitmall.agent.agent.service.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 聚合所有 ToolProvider 的工具目录。 启动时拒绝重复工具名，运行时按名字查找定义；本类不执行商城业务。 */
@Component
public class ToolRegistry {
    /** 按工具名称索引的只读目录，创建时已检查重名。 */
    private final Map<String, RegisteredTool> tools;

    /** Spring 注入各功能提供器后，遍历工具定义并检查重名，构建只读工具目录；这个过程不执行工具。 */
    public ToolRegistry(List<ToolProvider> providers) {
        Map<String, RegisteredTool> registered = new LinkedHashMap<>();
        for (ToolProvider provider : providers) {
            for (RegisteredTool tool : provider.tools()) {
                if (registered.putIfAbsent(tool.getName(), tool) != null) {
                    throw new IllegalArgumentException("重复工具注册：" + tool.getName());
                }
            }
        }
        tools = Map.copyOf(registered);
    }

    /** 返回工具定义列表的副本，调用方不能修改内部目录。 */
    public List<RegisteredTool> all() {
        return List.copyOf(tools.values());
    }

    /** 按名称取得工具定义；未知工具明确报错，不默认执行任何业务。 */
    public RegisteredTool require(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未知工具：" + name);
        }
        return tool;
    }
}
