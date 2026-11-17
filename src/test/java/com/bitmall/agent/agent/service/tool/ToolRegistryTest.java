package com.bitmall.agent.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 工具注册冲突的单元测试。 两个功能模块提供同名工具时必须失败，避免一个工具悄悄覆盖另一个工具。 */
class ToolRegistryTest {
    /** 两个模块注册同名工具时必须失败，不能静默覆盖。 */
    @Test
    void conflictingFeatureDefinitionsFailInsteadOfOverwritingATool() {
        var tool =
                new RegisteredTool(
                        "echo",
                        "test",
                        "{}",
                        null,
                        RegisteredTool.Risk.READ,
                        (context, args) -> Map.of(),
                        (context, args) -> "test");
        ToolProvider first = () -> List.of(tool);
        ToolProvider second = () -> List.of(tool);
        assertThatThrownBy(() -> new ToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复工具注册：echo");
    }
}
