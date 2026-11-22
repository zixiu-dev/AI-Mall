package com.bitmall.agent.agent.service.tool.support;

import static com.bitmall.agent.agent.service.tool.RegisteredTool.Risk.WRITE;

import com.bitmall.agent.agent.service.tool.RegisteredTool;

import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 构造工具定义和 JSON Schema 的辅助类。 把通用的参数格式与回调包装集中在这里，各功能模块只描述自己的工具。 */
public final class ToolDefinitions {
    /** 没有业务参数的工具所用 Schema，并禁止传入额外字段。 */
    public static final String NO_ARGS =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    /** 本类只提供静态辅助方法，不允许外部创建无意义的实例。 */
    private ToolDefinitions() {}

    /** 创建一般工具定义，保存描述、Schema、技能要求和执行函数。 */
    public static RegisteredTool tool(
            String name,
            String description,
            String schema,
            String skill,
            RegisteredTool.Risk risk,
            RegisteredTool.ToolHandler handler) {
        return new RegisteredTool(
                name, description, schema, skill, risk, handler, (ctx, args) -> name + " 写操作");
    }

    /** 创建写工具定义，同时绑定真实执行函数和确认前预览函数。 */
    public static RegisteredTool writeTool(
            String name,
            String description,
            String schema,
            String skill,
            UserWriteHandler handler,
            UserPreview preview) {
        return new RegisteredTool(
                name,
                description,
                schema,
                skill,
                WRITE,
                (ctx, args) -> handler.execute(ctx.getUserId(), args),
                (ctx, args) -> preview.describe(ctx.getUserId(), args));
    }

    /** 创建没有必填字段的对象参数 Schema。 */
    public static String schema(String... pairs) {
        return buildSchema(new String[0], pairs);
    }

    /** 把字段名称、类型和必填项拼成 JSON Schema，并禁止模型传入额外字段。 */
    private static String buildSchema(String[] required, String... pairs) {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                properties.append(',');
            }
            properties
                    .append('"')
                    .append(pairs[i])
                    .append("\":{\"type\":\"")
                    .append(pairs[i + 1])
                    .append("\"}");
        }
        String requiredJson =
                Arrays.stream(required)
                        .map(value -> "\"" + value + "\"")
                        .collect(Collectors.joining(","));
        return "{\"type\":\"object\",\"properties\":{"
                + properties
                + "},\"required\":["
                + requiredJson
                + "],\"additionalProperties\":false}";
    }

    /** 创建带必填字段列表的对象参数 Schema。 */
    public static String requiredSchema(String name, String type) {
        return buildSchema(new String[] {name}, name, type);
    }

    /** 创建带必填字段列表的对象参数 Schema。 */
    public static String requiredSchema(String[] required, String... pairs) {
        return buildSchema(required, pairs);
    }

    /** 只接收用户编号和业务参数的写操作函数约定。 ToolDefinitions 把它包装成包含完整上下文的工具处理器。 */
    @FunctionalInterface
    public interface UserWriteHandler {
        /** 按用户和明确业务参数执行写操作；事务和业务校验由对应 Service 负责。 */
        Object execute(long userId, Map<String, Object> arguments);
    }

    /** 只接收用户编号和业务参数的确认预览函数约定。 返回一段操作说明，不提交实际业务变更。 */
    @FunctionalInterface
    public interface UserPreview {
        /** 为当前用户生成写操作的预览说明，不执行实际写入。 */
        String describe(long userId, Map<String, Object> arguments);
    }
}
