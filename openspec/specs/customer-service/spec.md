# 物流售后与规则查询

## Purpose

约定助手可查询的订单物流、售后资格和商城规则，以及用户确认后可提交的售后申请，强调数据归属、金额复核和申请与真实退款的区别，不将预检或规则检索误报为业务已经执行成功。

## 当前实现

| 工具 | 当前执行过程 |
|---|---|
| `logistics_query` | 按当前用户核对订单归属，查询本库物流轨迹。 |
| `after_sales_precheck` | 检查订单归属、状态、金额和已有申请，返回资格及金额。 |
| `after_sales_submit` | 用户确认后再校验资格，创建 `REVIEWING` 售后申请。 |
| `after_sales_query` | 按用户及订单号或售后单号查询进度。 |
| `search_knowledge` | 按品牌、政策类别与渠道查询证据，引用文档来源。对本站订单的办理资格仍由业务服务判断。 |

物流与售后通过 Agent 工具使用服务端用户上下文。当前轨迹来自本地数据库，申请流程记录受理和进度；真实物流回调与退款渠道未接入。

[售后服务](../../../src/main/java/com/bitmall/agent/aftersales/service/AfterSalesService.java) · [物流查询服务](../../../src/main/java/com/bitmall/agent/order/service/OrderService.java) · [政策检索 Spec](../knowledge-retrieval/spec.md)

## Requirements

### Requirement: SUPPORT-001 物流查询归属

系统 SHALL 在查询物流轨迹前核对订单属于当前用户，再返回本库物流事件。

#### Scenario: 他人订单

- **WHEN** 当前用户查询不属于自己的订单物流
- **THEN** 拒绝或报告不可查询，不返回他人轨迹。

### Requirement: SUPPORT-002 售后预检与确认执行分离

系统 SHALL 在订单处于当前支持的 SHIPPED / COMPLETED 且未查到已有申请时提供预检；正式提交重新校验资格与用户确认金额，通过后记录 REVIEWING 申请。

#### Scenario: 只做售后预检

- **WHEN** 调用售后预检工具
- **THEN** 返回资格、原因及可申请金额，不插入申请，不执行退款。

#### Scenario: 金额或资格变化

- **GIVEN** 用户已确认售后提议
- **WHEN** 正式执行时金额不同或资格不成立
- **THEN** 拒绝过时内容，不能静默修改确认金额。

#### Scenario: 提交并查询申请

- **GIVEN** 用户批准有效售后提议
- **WHEN** 完成申请写入并按本人单号/订单查询
- **THEN** 返回真实售后申请状态，不能把 REVIEWING 说成退款到账。

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

### Requirement: SUPPORT-004 品牌和文档类别意图

系统 SHALL 识别受支持品牌、别名、产品家族及退换货/保修/配送等文档类别；显式参数与问题冲突时补问，未知品牌或缺少必要类别时不任意补齐，未命中不跨品牌回退。

#### Scenario: 别名与多品牌

- **WHEN** 用户查询 iPhone 的退货或对比苹果与耐克退货
- **THEN** iPhone 归属 Apple，多品牌分别召回并标明身份，不合并为通用天数

#### Scenario: 缺失冲突或未收录

- **WHEN** 只说苹果、结构化品牌与原问题冲突、询问未收录品牌或未收录类别
- **THEN** 返回明确补问或缺少资料状态，不把无结果当允许退货或套用其他品牌规则

### Requirement: SUPPORT-005 政策引用与执行边界

系统 SHALL 区分本平台交易规则与品牌对应渠道政策，引用来源及核对日期；知识文档为数据而非工具指令，不能凭检索改变订单或生成已退款结论。

#### Scenario: 本商城购买的品牌商品

- **WHEN** 用户询问本站买的 iPhone 如何退货
- **THEN** 先说明平台办理规则与归属校验，品牌官方渠道政策只作明确标注的参考，不直接套用 Apple Store 退货窗口

#### Scenario: 可点击来源

- **WHEN** 助手引用品牌文档
- **THEN** 页面能打开对应 HTTPS 来源，脚本或其他非 HTTPS 链接不被当作政策来源执行

## Current limitations

这些能力通过内部 Service 和 Agent 工具开放，没有对应独立售后/物流 REST Controller。没有真实物流回调和退款渠道；并发售后防重复仍需更强约束，不能宣称已完整解决。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java)
