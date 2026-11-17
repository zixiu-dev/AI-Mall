-- MySQL 8.0.16+ / InnoDB
CREATE TABLE coupon (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    threshold_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(12,2) NOT NULL,
    total_stock         INTEGER NOT NULL,
    claimed_count       INTEGER NOT NULL DEFAULT 0,
    start_at            DATETIME(6) NOT NULL,
    end_at              DATETIME(6) NOT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_coupon (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    coupon_id           BIGINT NOT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    claimed_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    used_order_id       VARCHAR(40),
    used_at             DATETIME(6),
    UNIQUE (user_id, coupon_id),
    CONSTRAINT fk_user_coupon_user_id FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_coupon_coupon_id FOREIGN KEY (coupon_id) REFERENCES coupon(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cart_item (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    sku_id              BIGINT NOT NULL,
    quantity            INTEGER NOT NULL,
    selected            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (user_id, sku_id),
    CONSTRAINT fk_cart_item_user_id FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_cart_item_sku_id FOREIGN KEY (sku_id) REFERENCES product_sku(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE customer_order ADD COLUMN coupon_id BIGINT;
ALTER TABLE customer_order ADD COLUMN coupon_name VARCHAR(120);

CREATE INDEX idx_user_coupon_user_status ON user_coupon(user_id, status);
CREATE INDEX idx_cart_item_user ON cart_item(user_id, selected);

INSERT INTO coupon(id, name, threshold_amount, discount_amount, total_stock, claimed_count, start_at, end_at, status) VALUES
(3001, '新人专享 · 满 99 减 15', 99.00, 15.00, 10000, 1, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE'),
(3002, '品质生活 · 满 500 减 80', 500.00, 80.00, 5000, 0, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE'),
(3003, '大额焕新 · 满 3000 减 300', 3000.00, 300.00, 1000, 0, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE'),
(3004, '全场通用 · 无门槛减 8', 0.00, 8.00, 20000, 0, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2027-12-31 23:59:59', 'ACTIVE');

INSERT INTO user_coupon(user_id, coupon_id, status, claimed_at) VALUES
(10001, 3001, 'AVAILABLE', TIMESTAMP '2026-10-01 09:00:00');

INSERT INTO cart_item(user_id, sku_id, quantity, selected) VALUES
(10001, 1003, 1, TRUE),
(10001, 1008, 1, TRUE);

INSERT INTO agent_skill(code, name, description, manual, tool_names) VALUES
('coupon_ops', '优惠券办理', '领取优惠券时使用', '先查询当前可领取优惠券及剩余库存，确认用户尚未领取且活动有效；领取前展示优惠券名称、门槛、优惠金额和有效期，得到用户确认后才能执行。', '["coupon_claim"]');
