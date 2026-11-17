-- 历史 V12 不修改；新版本删除绑定 SKU 的活动模型。升级前回调会拒绝尚未排空的旧异步任务。
ALTER TABLE order_submit_trace DROP COLUMN activity_id;
DROP TABLE order_sale_activity;
ALTER TABLE product_sku ADD COLUMN locked_stock INTEGER NOT NULL DEFAULT 0;
ALTER TABLE product_sku ADD CONSTRAINT chk_sku_nonnegative_stock CHECK (stock >= 0 AND locked_stock >= 0);
ALTER TABLE customer_order ADD COLUMN payment_expires_at TIMESTAMP;
CREATE INDEX idx_order_payment_timeout ON customer_order(status, payment_expires_at);

-- 兼容旧版本可能已有的待支付订单：旧 stock 已扣减，此处只补齐锁定账，不再扣可售。
UPDATE product_sku SET locked_stock = COALESCE((SELECT SUM(i.quantity) FROM order_item i
    JOIN customer_order o ON o.id=i.order_id WHERE i.sku_id=product_sku.id AND o.status='PENDING_PAYMENT'),0);
UPDATE customer_order SET payment_expires_at=CURRENT_TIMESTAMP WHERE status='PENDING_PAYMENT';
UPDATE user_coupon SET status='LOCKED', used_at=NULL WHERE status='USED'
    AND used_order_id IN (SELECT id FROM customer_order WHERE status='PENDING_PAYMENT');

-- 已终结的旧异步命令不再投递；历史 trace 和订单保留原记录。
UPDATE order_message_outbox SET status='SENT', locked_until=NULL, last_error='旧异步建单入口已退役'
    WHERE event_type='ORDER_SUBMIT' AND status <> 'SENT';

-- 技能手册同样遵守新订单状态，不引导模型取消已支付订单或宣称下单即支付。
UPDATE agent_skill SET manual=REPLACE(manual,'仅 PENDING_PAYMENT、PAID 允许直接取消','仅 PENDING_PAYMENT 允许直接取消；PAID 必须走售后') WHERE code='order_cancel_flow';
UPDATE agent_skill SET manual=CONCAT(manual,' 确认建单成功后状态为 PENDING_PAYMENT，应提示用户支付，不得声称已付款或已扣款。') WHERE code='order_create_flow';
