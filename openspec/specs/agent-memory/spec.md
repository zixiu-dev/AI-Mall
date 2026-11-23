# 会话历史、模型上下文与偏好

## Purpose

约定页面消息、模型原生工具上下文、较早轮次摘要和跨会话偏好的保存与读取边界，保证恢复聊天时保持工具调用关系完整，控制上下文体积，并避免把临时交易事实或模型推断自动写成长期用户偏好。

## 当前实现

| 数据 | 用途 |
|---|---|
| `conversation`、`chat_message` | 页面历史、摘要及水位；`GET /api/agent/conversations/{conversationId}/messages` 按归属读取。 |
| `agent_scope_state` | Spring AI 消息、工具调用和工具结果，模型状态使用 `spring-ai-v1` 键保存。 |
| `user_memory` | 保存用户明确授权长期记住的偏好。 |
| `conversation_skill` | 维护当前会话的技能加载状态；工具授权还需检查模型可见的手册结果。 |

模型上下文按完整轮次裁剪，默认保留最近 6 轮且有 32000 字符上限。工具调用与结果一起保留，较早对话由摘要辅助；已经裁掉的技能文档不再为工具提供授权依据。

### 旧会话怎么处理

会话第一次进入 Spring AI 时，从普通聊天历史恢复，技能手册须重新加载。已保存但缺少结果的工具调用在下一轮补上中断回执，不自动重放写操作，实际交易状态通过业务服务查询。

[历史裁剪](../../../src/main/java/com/bitmall/agent/agent/service/conversation/AgentHistorySelector.java) · [用户偏好](../../../src/main/java/com/bitmall/agent/agent/service/memory/MemoryService.java)

打开商城助手时，页面通过 `GET /api/agent/conversations/{conversationId}/snapshot` 恢复近期消息、仍待处理的确认卡与已决定执行的购买进度。购买按已有操作来源查 trace，仍在处理的购买继续使用原进度接口。

## Requirements

### Requirement: MEMORY-001 页面历史归属

系统 SHALL 在读取 GET /api/agent/conversations/{conversationId}/messages 时核对会话归属，并返回持久化页面消息。

#### Scenario: 刷新后读取会话

- **GIVEN** 会话消息已保存且属于当前用户
- **WHEN** 读取会话历史
- **THEN** 能够展示已保存消息；不能跨用户读取他人会话。

### Requirement: MEMORY-002 原生工具关系与完整轮次

系统 SHALL 在恢复和裁剪模型上下文时保持工具调用与工具结果成对，按完整轮次保留近期内容，并有可解释的长度预算。

#### Scenario: 长会话裁剪

- **GIVEN** 会话超过当前上下文预算
- **WHEN** 准备下一轮推理
- **THEN** 不能只留下工具请求而丢掉其对应结果，或继续以已裁剪手册开放技能。

### Requirement: MEMORY-003 摘要水位与明确偏好

系统 SHALL 使用已处理消息水位避免重复摘要；仅在用户明确要求保存偏好时写长期偏好，不把变化的商品、库存或订单状态当长期记忆。

#### Scenario: 用户明确记住尺码

- **WHEN** 用户要求记住稳定鞋码偏好
- **THEN** 保存用户偏好并可跨会话读取。

#### Scenario: 查询实时订单

- **GIVEN** 历史里有旧订单信息
- **WHEN** 用户要求当前订单状态
- **THEN** 仍通过业务工具读取当前数据，不能用旧摘要替代。

### Requirement: MEMORY-004 框架迁移与中断恢复

系统 SHALL 使用独立版本键持久化 Spring AI 消息，不把旧框架状态直接反序列化或重放业务；旧会话通过可见页面历史及后端回执继续。新状态出现未配对工具调用时补中断结果，不能自动重试未知业务效果。

#### Scenario: 旧框架会话继续聊天

- **GIVEN** 会话仅存在旧框架状态与页面历史
- **WHEN** Spring AI 首次接管该会话
- **THEN** 恢复用户和助手历史，保留原记录；技能须重新从真实加载结果获得。

#### Scenario: 工具结果落库前中断

- **GIVEN** 新状态中工具调用已保存但结果缺失
- **WHEN** 下一轮恢复
- **THEN** 为原调用补中断结果并保持协议配对，要求通过业务查询确认实际状态，不自动重放。

### Requirement: MEMORY-005 会话页面恢复

系统 SHALL 在读取会话快照时校验归属，返回近期可展示消息、当前待确认操作及近期已决定执行的下单操作所关联的购买进度。

#### Scenario: 刷新会话

- **WHEN** 用户重新打开已有会话
- **THEN** 页面恢复消息与当前待确认卡，并继续查询处理中购买；已完成的确认卡不得恢复为可点击。

## Current limitations

当前摘要是确定性整理而非模型语义摘要；上下文预算按序列化字符计算，不等于精确 token 预算。持久化模型状态也不代表多实例会话已分布式串行。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/agent/service/runtime/SpringAiIntegrationTest.java)
