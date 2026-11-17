## MODIFIED Requirements

### Requirement: AGENT-001 真实模式与演示模式可区分

系统 SHALL 明确展示 spring-ai 或 demo 配置模式，真实模型失败时报告失败，不自动用规则助手冒充真实模型正常响应。

#### Scenario: 查询模式

- **WHEN** 读取 GET /api/agent/status
- **THEN** 返回配置模式；状态接口本身不证明远端模型此刻调用成功。

#### Scenario: 真实模型不可用

- **GIVEN** 应用启用真实模型
- **WHEN** 模型调用失败
- **THEN** 明确报告不可用或需要核对结果，不伪装为成功调用模型。

