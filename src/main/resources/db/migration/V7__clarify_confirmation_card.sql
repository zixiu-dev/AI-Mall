UPDATE agent_skill
SET manual = manual || '
8. 确认卡必须真实创建：checkout_quote 只报价，不生成确认按钮。用户已明确购买、规格数量完整且试算成功时，立即调用 order_create 生成待确认记录；不要以文字表格代替确认卡，不要在调用 order_create 前再要求用户文字确认。用户只要求查询或试算时，不得擅自发起订单。仅在写工具返回 pendingActionId 后提示用户点击页面按钮。'
WHERE code = 'order_ops';
