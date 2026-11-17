-- 本地消息表：和订单一起提交，后台负责发送，不在订单事务里访问 MQ。
CREATE TABLE order_message_outbox (
    event_id        VARCHAR(40) PRIMARY KEY,
    order_id        VARCHAR(40) NOT NULL,
    topic           VARCHAR(127) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token     VARCHAR(40),
    locked_until    TIMESTAMP,
    broker_msg_id   VARCHAR(200),
    last_error      VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP,
    CONSTRAINT chk_order_message_status CHECK (status IN ('PENDING', 'SENDING', 'SENT'))
);

CREATE INDEX idx_order_message_pending ON order_message_outbox(status, next_attempt_at, locked_until);

-- 消费回执：event_id 唯一，重复投递不会重复落库。当前消费用途是订单事件审计。
CREATE TABLE order_event_receipt (
    event_id        VARCHAR(40) PRIMARY KEY,
    order_id        VARCHAR(40) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    user_id         BIGINT NOT NULL,
    pay_amount      NUMERIC(12,2) NOT NULL,
    occurred_at     TIMESTAMP NOT NULL,
    received_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_event_receipt_order ON order_event_receipt(order_id, received_at);
