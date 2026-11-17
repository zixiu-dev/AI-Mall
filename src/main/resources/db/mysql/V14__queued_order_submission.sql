-- MySQL 8.0.16+，仅增加可靠排队需要的处理期限与命令取消状态。
ALTER TABLE order_submit_trace ADD COLUMN processing_expires_at DATETIME(6);
CREATE INDEX idx_order_trace_pending ON order_submit_trace(status,mode,next_check_at,processing_expires_at);
ALTER TABLE order_message_outbox DROP CHECK chk_order_message_status;
ALTER TABLE order_message_outbox ADD CONSTRAINT chk_order_message_status
    CHECK (status IN ('PENDING','SENDING','SENT','CANCELLED'));
