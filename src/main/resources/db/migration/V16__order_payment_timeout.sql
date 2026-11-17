-- 支付超时命令与订单同事务落库；绝对毫秒时间确保 Outbox 重试不会重新等待 15 分钟。
ALTER TABLE order_message_outbox ADD COLUMN deliver_at_epoch_ms BIGINT;
ALTER TABLE order_message_outbox ADD CONSTRAINT chk_outbox_delivery_time
    CHECK (deliver_at_epoch_ms IS NULL OR deliver_at_epoch_ms > 0);
