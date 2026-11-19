# 优惠券领取与使用

## Purpose

约定券模板和用户持有券的区别、领取约束与订单生命周期中的占用规则，保证优惠资格和金额由后端检查，领取成功并不构成后续永远可用的承诺，失败或关闭订单时也能够恢复可用资格。

## 当前实现

- `GET /api/store/coupons` 查询券活动与当前用户领用状态。活动用 `couponId`，领到的那张券用 `userCouponId`。
- `POST /api/store/coupons/{couponId}/claim` 检查领取条件，以数据库条件更新控制领取上限，在同一事务写用户券。唯一约束防同一用户重复领取，事务失败时领用计数随之回滚。
- 试算校验门槛、有效期和归属。建单时重新核对并锁券，支付时核销，取消与超时关单时释放。券状态和订单结果随交易事务提交。

[优惠券服务](../../../src/main/java/com/bitmall/agent/coupon/service/CouponService.java) · [支付与取消 Spec](../order-lifecycle/spec.md)

## Requirements

### Requirement: COUPON-001 券查询与领取

系统 SHALL 区分 couponId 和 userCouponId，并在有效期、可领取库存和当前用户领取资格满足时领取；同用户同模板当前仅允许领取一次。

#### Scenario: 查询用户领用状态

- **WHEN** 调用 GET /api/store/coupons
- **THEN** 返回可展示券及当前用户相关状态。

#### Scenario: 重复或超额领取

- **GIVEN** 用户已领取或券库存不足
- **WHEN** 调用 POST /api/store/coupons/{couponId}/claim
- **THEN** 明确拒绝，不增加可领取库存的消耗。

### Requirement: COUPON-002 成交时重新核对资格

系统 SHALL 在试算和建单时核对券归属、状态、有效期与门槛，不能仅凭曾领取成功就抵扣。

#### Scenario: 确认后资格变化

- **GIVEN** 用户看到报价后券状态或有效期变化
- **WHEN** 执行建单
- **THEN** 重新校验，不静默使用已经不符合条件的券。

### Requirement: COUPON-003 券占用与订单一致

系统 SHALL 在建单时锁定用户券，支付后核销，关闭待支付订单时释放，且与相应订单状态变更保持数据库事务一致。

#### Scenario: 关闭订单释放券

- **GIVEN** 用户券已被待支付订单锁定
- **WHEN** 订单取消或超时关闭
- **THEN** 券恢复可用；重复关闭不产生额外抵扣或返还。

#### Scenario: 建单失败

- **GIVEN** 建单事务使用了用户券
- **WHEN** 事务回滚
- **THEN** 不能遗留无订单对应的本次锁券结果。

## References

[验证入口](../../../src/test/java/com/bitmall/agent/order/service/OrderSubmissionIntegrationTest.java)
