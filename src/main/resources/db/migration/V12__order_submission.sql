-- 活动模式由后台配置，普通 SKU 没有活动记录时按同步处理；切换模式必须先排空在途请求。
CREATE TABLE order_sale_activity (
    id VARCHAR(64) PRIMARY KEY,
    sku_id BIGINT NOT NULL UNIQUE REFERENCES product_sku(id),
    name VARCHAR(120) NOT NULL,
    mode VARCHAR(8) NOT NULL CHECK (mode IN ('SYNC', 'ASYNC')),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    redis_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    max_pending INTEGER NOT NULL DEFAULT 200,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    CHECK (max_pending > 0 AND ends_at > starts_at)
);

-- 一次购买意图对应一个 trace，刷新和网络重试沿用原编号；同参数的新购买使用新编号。
CREATE TABLE order_submit_trace (
    trace_id VARCHAR(40) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    origin_key VARCHAR(80) UNIQUE,
    mode VARCHAR(8) NOT NULL CHECK (mode IN ('SYNC', 'ASYNC')),
    activity_id VARCHAR(64) REFERENCES order_sale_activity(id),
    request_snapshot TEXT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INIT'
        CHECK (status IN ('INIT', 'PROCESSING', 'SUCCESS', 'FAILED', 'EXPIRED')),
    reserve_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    order_id VARCHAR(40),
    result_json TEXT,
    failure_code VARCHAR(64),
    failure_reason VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    next_check_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);
CREATE INDEX idx_order_trace_recovery ON order_submit_trace(status, reserve_status, next_check_at);
CREATE INDEX idx_order_trace_user ON order_submit_trace(user_id, created_at);
ALTER TABLE customer_order ADD COLUMN submit_trace_id VARCHAR(40) REFERENCES order_submit_trace(trace_id);
CREATE UNIQUE INDEX uk_customer_order_trace ON customer_order(submit_trace_id);
ALTER TABLE order_message_outbox ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE order_message_outbox ADD COLUMN trace_id VARCHAR(40) REFERENCES order_submit_trace(trace_id);
CREATE UNIQUE INDEX uk_order_message_trace_type ON order_message_outbox(trace_id, event_type);

-- 默认关闭，方便零依赖演示；启用前按文档配置库存 Redis 与 MQ。
INSERT INTO order_sale_activity(id, sku_id, name, mode, enabled, starts_at, ends_at)
VALUES ('demo-hot-sale', 1004, '演示活动：异步购买', 'ASYNC', FALSE,
        TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2099-01-01 00:00:00');
