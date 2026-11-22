# 简化依赖注入与字段职责说明

## Why

用户阅读代码时难以理解注入字段，重复的构造器赋值也遮挡了业务步骤。采用 Lombok @RequiredArgsConstructor 表达普通依赖注入，并在每个依赖字段旁说明它在本模块的作用。

## What Changes

- Controller、Service、Repository、消费者等普通依赖注入使用 Lombok 生成构造器。
- 字段上的 Qualifier 与 Value 复制到构造参数，保持具名 Redis、线程池和配置值的装配语义。
- 工具提供器在 tools() 中声明工具列表，注入字段保持 final。
- 保留需要构造即完成资源初始化、注册监控、校验或建立不可变结构的定制构造器。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。skip_specs。本次是行为不变的代码与注释重构，沿用现有所有能力契约。

## Impact

增加 Lombok 编译期依赖与注解复制配置，改写依赖声明和注释；验证 Spring 启动、配置值、具名依赖、缓存、交易、消息和 Agent 的既有测试。
