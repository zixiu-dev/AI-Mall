INSERT INTO agent_skill(code, name, description, manual, tool_names) VALUES
('order_create_flow', '下单办理', '用户明确要求购买或创建订单时加载', '下单办理流程：
1. 创建订单：先通过商品查询、详情或已有会话信息确定真实 SKU 和数量，缺少规格或数量时补问，不得猜测商品或 SKU ID。
2. 优惠检查：确定 SKU 和数量后，默认调用 coupon_list 查询候选优惠券及用户领取状态；用户明确要求不用券时跳过。当前只支持商城通用满减券，每笔订单最多使用一张，不支持多券叠加。候选列表不代表最终可用，也不保证最优优惠，资格和金额以后端试算为准。
3. 已有券：选用用户已领取且可用的 userCouponId 调用 checkout_quote。尚未领券：先说明门槛、优惠金额和有效期，加载 coupon_ops，使用活动 couponId 调用 coupon_claim 生成待确认记录；收到 AWAITING_CONFIRMATION 后立即停止，等待用户点击确认，不得声称已领取或继续建单。后续对话以后台回执和 coupon_list 查询结果确认领取成功，取得 userCouponId 后再试算。不能用活动 couponId 冒充用户券 ID。
4. 结算：调用 checkout_quote 校验商品和优惠券并取得商品明细、地址及 payAmount。已知 SKU 时不要猜商品 ID 或遍历商品；必要时可先无券试算了解当前金额，但创建订单前应完成优惠检查，并按最终选定方案重新试算。没有合适优惠券时说明不用券。不要自行计算或猜测成交金额。
5. 下单确认：核对具体商品、规格、数量、地址和最终金额。将试算返回的 addressId、payAmount 分别传入 order_create 的 addressId、expectedPayAmount，用券时保持相同 userCouponId。order_create 仅生成待确认记录，不代表建单成功；提示用户核对页面确认卡，得到后台执行成功回执后才能告知成功。
6. 所有交易写操作必须经页面确认。用户文字确认不能代替确认接口；等待确认时立即结束本轮。资格、价格或库存变化时，说明后端错误并重新查询、试算或补问，不得原样反复调用。
7. 确认卡必须真实创建：checkout_quote 只报价。用户明确购买且信息完整、试算成功后，调用 order_create；不得以文字表格代替确认卡。只查询或试算不得发起下单。只有工具返回 pendingActionId 后才能提示点击页面按钮。', '["order_create"]'),
('order_cancel_flow', '取消订单', '用户明确要求取消已有订单时加载', '取消订单办理流程：
1. 意图确认：只有用户明确要求取消订单才进入办理；仅查询状态、询问规则时不要生成取消申请。
2. 定位订单：调用 order_query 查询当前用户的订单；订单号来自用户或真实查询结果，不得猜测。目标不明确或有多个候选时补问，不得擅自选择。
3. 状态检查：仅 PENDING_PAYMENT、PAID 允许直接取消；已取消告知现状，其他状态说明不能直接取消，不得尝试绕过。后端生成确认卡及执行时都会重新校验。
4. 展示影响：展示订单号、商品、订单金额和取消影响。取消后返还库存并解除优惠券占用；返还的券仍受有效期和使用条件限制。当前是演示交易，没有接入真实支付退款，不得承诺到账或原路退款。
5. 准备确认：调用 order_cancel(orderId) 生成真实待确认记录，不能以文字表格代替确认卡。仅返回 pendingActionId 后提示点击“确认取消订单”；“暂不取消”表示放弃本次申请，不会取消订单。
6. 等待用户：收到 AWAITING_CONFIRMATION 后立即结束本轮。文字回复不能替代页面确认，不得自动确认或声称已取消。
7. 结果处理：以后端执行回执为准。状态变化时重新查询；结果不明时先查订单，不得原样反复调用。禁止将取消请求转成下单或其他写操作。', '["order_cancel"]');
UPDATE agent_skill SET enabled = FALSE WHERE code = 'order_ops';
DELETE FROM conversation_skill WHERE skill_code = 'order_ops';
