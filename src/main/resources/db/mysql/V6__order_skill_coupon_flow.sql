-- MySQL 8.0.16+ / InnoDB
UPDATE agent_skill
SET description = '用户明确要求购买、创建订单或取消订单时优先加载',
    manual = '订单办理流程：
1. 创建订单：先通过商品查询、详情或已有会话信息确定真实 SKU 和数量，缺少规格或数量时补问，不得猜测商品或 SKU ID。
2. 优惠检查：确定 SKU 和数量后，默认调用 coupon_list 查询候选优惠券及用户领取状态；用户明确要求不用券时跳过。当前只支持商城通用满减券，每笔订单最多使用一张，不支持多券叠加。候选列表不代表最终可用，也不保证最优优惠，资格和金额以后端试算为准。
3. 已有券：选用用户已领取且可用的 userCouponId 调用 checkout_quote。尚未领券：先说明门槛、优惠金额和有效期，加载 coupon_ops，使用活动 couponId 调用 coupon_claim 生成待确认记录；收到 AWAITING_CONFIRMATION 后立即停止，等待用户点击确认，不得声称已领取或继续建单。后续对话以后台回执和 coupon_list 查询结果确认领取成功，取得 userCouponId 后再试算。不能用活动 couponId 冒充用户券 ID。
4. 结算：调用 checkout_quote 校验商品和优惠券并取得商品明细、地址及 payAmount。已知 SKU 时不要猜商品 ID 或遍历商品；必要时可先无券试算了解当前金额，但创建订单前应完成优惠检查，并按最终选定方案重新试算。没有合适优惠券时说明不用券。不要自行计算或猜测成交金额。
5. 下单确认：核对具体商品、规格、数量、地址和最终金额。将试算返回的 addressId、payAmount 分别传入 order_create 的 addressId、expectedPayAmount，用券时保持相同 userCouponId。order_create 仅生成待确认记录，不代表建单成功；提示用户核对页面确认卡，得到后台执行成功回执后才能告知成功。
6. 取消订单：先调用 order_query 确认目标订单和当前状态，展示订单、金额和取消影响，再调用 order_cancel 生成待确认记录。
7. 所有交易写操作必须经页面确认。用户文字确认不能代替确认接口；等待确认时立即结束本轮。资格、价格或库存变化时，说明后端错误并重新查询、试算或补问，不得原样反复调用。'
WHERE code = 'order_ops';
