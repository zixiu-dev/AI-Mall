## MODIFIED Requirements

### Requirement: SUPPORT-003 规则检索有据可查

系统 SHALL 按用户问题和结构化品牌、文档类别检索数据库规则，返回标题、正文、来源、适用渠道、地区、版本及核对日期；品牌与类别是明确过滤条件，不能通过关键词分数放宽。

#### Scenario: 查询退货规则

- **WHEN** 助手调用 search_knowledge
- **THEN** 提供本库相关规则内容；按实际 retrievalMode 描述检索能力；纯关键词或降级结果不能宣称完整混合检索。

#### Scenario: 指定品牌类别

- **WHEN** 用户询问苹果退货政策
- **THEN** 只返回 Apple 退换货资料，附来源及适用渠道，不混入小米或本平台退货条件

#### Scenario: 多知识子问题和真实模式

- **WHEN** 外层 Agent 将多个知识子问题交给 search_knowledge
- **THEN** 返回逐组证据、资料覆盖缺口和实际检索模式，遵守 knowledge-retrieval 能力的参数、版本与故障契约

