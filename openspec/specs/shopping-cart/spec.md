# 用户购物车

## Purpose

约定用户管理待购买 SKU、数量与勾选状态的行为，明确购物车展示与最终交易之间的边界，保证条目操作带有用户归属范围，并避免把购物车中的可见库存检查解释为已经预留库存。

## 当前实现

| 接口 | 执行过程 |
|---|---|
| `GET /api/store/cart` | 按用户读取条目，汇总勾选数量与金额。 |
| `POST /api/store/cart` | 校验 SKU、数量和库存提示，在原 SKU 条目上累加数量。 |
| `PATCH /api/store/cart/{itemId}` | 核对归属，更新数量或勾选状态。 |
| `DELETE /api/store/cart/{itemId}` | 按用户及条目 ID 删除，返回更新后的购物车。 |

购物车保存条目与选中状态；库存由下单时统一预扣。结算重查已选条目，正式下单绑定的快照独立保存，之后修改购物车不会改动原购买。

[购物车服务](../../../src/main/java/com/bitmall/agent/cart/service/CartService.java) · [结算 Spec](../checkout/spec.md)

## Requirements

### Requirement: CART-001 读取用户购物车

系统 SHALL 仅返回目标用户的购物车条目，并汇总数量和当前勾选金额。

#### Scenario: 读取购物车

- **WHEN** 调用 GET /api/store/cart
- **THEN** 返回该用户的条目和展示汇总，不混入其他用户数据。

### Requirement: CART-002 添加与修改条目

系统 SHALL 校验数量范围 1～20 及当前可用库存，同用户同 SKU 添加采用累加语义；修改、勾选和删除必须限定归属。

#### Scenario: 重复添加相同 SKU

- **GIVEN** 购物车已有相同 SKU
- **WHEN** 调用 POST /api/store/cart 添加合法数量
- **THEN** 合并为该用户同 SKU 条目；此接口不具有订单 trace 的永久去重语义。

#### Scenario: 修改与删除

- **GIVEN** 条目属于当前用户
- **WHEN** 调用 PATCH 或 DELETE /api/store/cart/{itemId}
- **THEN** 按请求更新数量/勾选或删除；其他用户不能凭条目 ID 操作。

### Requirement: CART-003 购物车不预留库存

系统 SHALL 将购物车价格和库存检查视为展示与提前提示，真正结算时重新核对交易条件。

#### Scenario: 购物车与成交之间库存变化

- **GIVEN** 用户已把商品加入购物车
- **WHEN** 库存变化后用户试算或提交订单
- **THEN** 以后端当前规则重新校验，不承诺加入购物车时的库存已经锁定。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/integration/CommerceModulesIntegrationTest.java)
