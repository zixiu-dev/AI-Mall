-- MySQL 8.0.16+；升级前回调会拒绝尚未排空的旧异步任务，不能直接绕过 Redis 预占。
ALTER TABLE order_submit_trace DROP FOREIGN KEY fk_order_submit_trace_activity_id;
ALTER TABLE order_submit_trace DROP COLUMN activity_id;
DROP TABLE order_sale_activity;
ALTER TABLE product_sku ADD COLUMN locked_stock INTEGER NOT NULL DEFAULT 0;
ALTER TABLE product_sku ADD CONSTRAINT chk_sku_nonnegative_stock CHECK (stock >= 0 AND locked_stock >= 0);
ALTER TABLE customer_order ADD COLUMN payment_expires_at DATETIME(6);
CREATE INDEX idx_order_payment_timeout ON customer_order(status, payment_expires_at);

UPDATE product_sku s LEFT JOIN (SELECT i.sku_id,SUM(i.quantity) AS quantity FROM order_item i
    JOIN customer_order o ON o.id=i.order_id WHERE o.status='PENDING_PAYMENT' GROUP BY i.sku_id) x ON x.sku_id=s.id
    SET s.locked_stock=COALESCE(x.quantity,0);
UPDATE customer_order SET payment_expires_at=CURRENT_TIMESTAMP(6) WHERE status='PENDING_PAYMENT';
UPDATE user_coupon SET status='LOCKED', used_at=NULL WHERE status='USED'
    AND used_order_id IN (SELECT id FROM customer_order WHERE status='PENDING_PAYMENT');
UPDATE order_message_outbox SET status='SENT', locked_until=NULL, last_error='旧异步建单入口已退役'
    WHERE event_type='ORDER_SUBMIT' AND status <> 'SENT';

-- 技能手册同样遵守新订单状态，不引导模型取消已支付订单或宣称下单即支付。
UPDATE agent_skill SET manual=REPLACE(manual,'仅 PENDING_PAYMENT、PAID 允许直接取消','仅 PENDING_PAYMENT 允许直接取消；PAID 必须走售后') WHERE code='order_cancel_flow';
UPDATE agent_skill SET manual=CONCAT(manual,' 确认建单成功后状态为 PENDING_PAYMENT，应提示用户支付，不得声称已付款或已扣款。') WHERE code='order_create_flow';
