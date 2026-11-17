# 结算试算与成交参数

## Purpose

约定立即购买和购物车结算如何形成可确认报价，要求后端核对真实 SKU、用户地址和可用优惠并计算金额，明确报价是提交前的确认依据而不是已经建单或冻结资源的成功回执。

## Requirements

### Requirement: CHECKOUT-001 立即购买与购物车试算

系统 SHALL 支持 DIRECT 指定 SKU 与 CART 当前用户勾选条目，并核对数量、库存、地址归属、券资格与金额。

#### Scenario: 立即购买

- **WHEN** 向 POST /api/store/checkout/quote 提交 DIRECT 参数
- **THEN** 返回对应 SKU、数量、金额、地址及适用优惠的报价。

#### Scenario: 购物车结算

- **GIVEN** 当前用户有勾选条目
- **WHEN** 提交 CART 试算
- **THEN** 只对该用户选中条目试算，不把未选中或他人条目纳入。

### Requirement: CHECKOUT-002 服务端计算与再次核价

系统 SHALL 使用后端当前数据计算商品额、优惠、运费和应付额；正式提交必须再次核对用户确认过的金额及购买内容。

#### Scenario: 金额发生变化

- **GIVEN** 用户已看到报价
- **WHEN** 价格变化后提交原确认金额
- **THEN** 拒绝不一致的成交条件，提示重新确认，不能静默换金额。

#### Scenario: 同价购物内容被替换

- **GIVEN** 用户已确认购物车明细
- **WHEN** 提交时总价相同但 SKU/数量与确认明细不同
- **THEN** 不能仅凭总额相同就接受原购买意图。

### Requirement: CHECKOUT-003 报价不产生交易副作用

系统 SHALL 让试算保持只读，不创建订单、不预扣库存、不锁定用户券。

#### Scenario: 只浏览结算页

- **WHEN** 用户多次调用试算但未提交购买
- **THEN** 库存、订单数和用户券占用不因试算变化。

## References

[设计与流程](../../../docs/api-flows.md) · [验证入口](../../../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java)
