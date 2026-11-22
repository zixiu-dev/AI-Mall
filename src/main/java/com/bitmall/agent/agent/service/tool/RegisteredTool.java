package com.bitmall.agent.agent.service.tool;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/** 一个工具的完整定义。 把名称、参数格式、技能要求、读写类型、执行函数和预览函数放在一起；它描述工具，不负责判断本轮是否允许调用。 */
@Getter
public final class RegisteredTool {
    /** 模型调用时使用的唯一工具名称，例如 product_search。 */
    private final String name;

    /** 功能简述，帮助调用者判断用途。 */
    private final String description;

    /** 工具参数的 JSON Schema，告诉模型允许传哪些字段。 */
    private final String inputSchema;

    /** 调用此工具前必须加载的技能，为空表示默认开放。 */
    private final String requiredSkill;

    /** 业务风险类型：READ 为查询，WRITE 需要用户确认。 */
    private final Risk risk;

    /** 真正执行工具业务的函数。 */
    private final ToolHandler handler;

    /** 写操作生成确认卡前使用的预览函数。 */
    private final ActionPreview preview;

    /** 使用完整字段创建对象，创建后通过 Getter 读取。 构造方法上的 JsonCreator 和参数上的 JsonProperty 明确指定 JSON 字段的对应关系。 */
    @JsonCreator
    public RegisteredTool(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("inputSchema") String inputSchema,
            @JsonProperty("requiredSkill") String requiredSkill,
            @JsonProperty("risk") Risk risk,
            @JsonProperty("handler") ToolHandler handler,
            @JsonProperty("preview") ActionPreview preview) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.requiredSkill = requiredSkill;
        this.risk = risk;
        this.handler = handler;
        this.preview = preview;
    }

    /** 按全部字段比较内容，便于查询结果和缓存结果进行一致性判断。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        RegisteredTool that = (RegisteredTool) other;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(inputSchema, that.inputSchema)
                && Objects.equals(requiredSkill, that.requiredSkill)
                && Objects.equals(risk, that.risk)
                && Objects.equals(handler, that.handler)
                && Objects.equals(preview, that.preview);
    }

    /** 与 equals 使用相同字段，保证放入集合后的比较规则一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(name, description, inputSchema, requiredSkill, risk, handler, preview);
    }

    /** 返回字段摘要，供日志和本地调试查看。 */
    @Override
    public String toString() {
        return "RegisteredTool["
                + "name="
                + name
                + ", description="
                + description
                + ", inputSchema="
                + inputSchema
                + ", requiredSkill="
                + requiredSkill
                + ", risk="
                + risk
                + ", handler="
                + handler
                + ", preview="
                + preview
                + "]";
    }

    /** 工具的业务读写类型。 READ 可以直接查询，WRITE 先生成待确认操作，用户确认后才执行业务变更。 */
    public enum Risk {
        /** 业务查询类型；其中加载技能和保存偏好会更新助手状态，但不会直接交易。 */
        READ,
        /** 商城写操作类型，先生成持久化确认卡，用户批准后才执行处理函数。 */
        WRITE
    }

    /** 工具真正处理业务的函数约定。 接收后端上下文和已经校验的参数，返回业务结果；写处理器由确认流程触发。 */
    @FunctionalInterface
    public interface ToolHandler {
        /** 执行具体工具业务，返回可序列化结果；写操作只在确认执行流程调用处理器。 */
        Object execute(ToolExecutionContext context, Map<String, Object> arguments);
    }

    /** 写操作的预览函数约定。 核对真实业务数据并生成供用户阅读的操作说明，不在这里创建订单或领取优惠券。 */
    @FunctionalInterface
    public interface ActionPreview {
        /** 检查业务对象并返回确认摘要，不提交实际交易。 */
        String describe(ToolExecutionContext context, Map<String, Object> arguments);
    }
}
