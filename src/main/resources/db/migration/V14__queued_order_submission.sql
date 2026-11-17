-- 排队建单复用数据库库存和待支付生命周期，不恢复活动表或 Redis 库存池。
ALTER TABLE order_submit_trace ADD COLUMN processing_expires_at TIMESTAMP;
CREATE INDEX idx_order_trace_pending ON order_submit_trace(status,mode,next_check_at,processing_expires_at);
ALTER TABLE order_message_outbox DROP CONSTRAINT chk_order_message_status;
ALTER TABLE order_message_outbox ADD CONSTRAINT chk_order_message_status
    CHECK (status IN ('PENDING','SENDING','SENT','CANCELLED'));
