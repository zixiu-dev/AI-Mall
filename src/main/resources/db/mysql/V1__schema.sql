-- MySQL 8.0.16+ / InnoDB
CREATE TABLE app_user (
    id                  BIGINT PRIMARY KEY,
    nickname            VARCHAR(80) NOT NULL,
    phone               VARCHAR(32) NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_address (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    receiver            VARCHAR(80) NOT NULL,
    phone               VARCHAR(32) NOT NULL,
    province            VARCHAR(40) NOT NULL,
    city                VARCHAR(40) NOT NULL,
    district            VARCHAR(40) NOT NULL,
    detail              VARCHAR(200) NOT NULL,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_address_user_id FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE product (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(160) NOT NULL,
    category            VARCHAR(60) NOT NULL,
    brand               VARCHAR(60) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    image_key           VARCHAR(40) NOT NULL,
    rating              NUMERIC(2,1) NOT NULL,
    sales_count         INTEGER NOT NULL DEFAULT 0,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE product_sku (
    id                  BIGINT PRIMARY KEY,
    product_id          BIGINT NOT NULL,
    sku_code            VARCHAR(64) NOT NULL UNIQUE,
    spec                VARCHAR(160) NOT NULL,
    price               NUMERIC(12,2) NOT NULL,
    original_price      NUMERIC(12,2),
    stock               INTEGER NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_product_sku_product_id FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE customer_order (
    id                  VARCHAR(40) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    status              VARCHAR(32) NOT NULL,
    goods_amount        NUMERIC(12,2) NOT NULL,
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    freight_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    pay_amount          NUMERIC(12,2) NOT NULL,
    address_snapshot    VARCHAR(500) NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    paid_at             DATETIME(6),
    cancelled_at        DATETIME(6),
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_customer_order_user_id FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE order_item (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id            VARCHAR(40) NOT NULL,
    product_id          BIGINT NOT NULL,
    sku_id              BIGINT NOT NULL,
    product_name        VARCHAR(160) NOT NULL,
    sku_spec            VARCHAR(160) NOT NULL,
    unit_price          NUMERIC(12,2) NOT NULL,
    quantity            INTEGER NOT NULL,
    CONSTRAINT fk_order_item_order_id FOREIGN KEY (order_id) REFERENCES customer_order(id),
    CONSTRAINT fk_order_item_product_id FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_order_item_sku_id FOREIGN KEY (sku_id) REFERENCES product_sku(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE logistics_event (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id            VARCHAR(40) NOT NULL,
    event_time          DATETIME(6) NOT NULL,
    status              VARCHAR(40) NOT NULL,
    description         VARCHAR(500) NOT NULL,
    CONSTRAINT fk_logistics_event_order_id FOREIGN KEY (order_id) REFERENCES customer_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE after_sales (
    id                  VARCHAR(40) PRIMARY KEY,
    order_id            VARCHAR(40) NOT NULL,
    user_id             BIGINT NOT NULL,
    type                VARCHAR(32) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    refund_amount       NUMERIC(12,2),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_after_sales_order_id FOREIGN KEY (order_id) REFERENCES customer_order(id),
    CONSTRAINT fk_after_sales_user_id FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE knowledge_document (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    category            VARCHAR(60) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    keywords            VARCHAR(300) NOT NULL,
    content             MEDIUMTEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_skill (
    code                VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(300) NOT NULL,
    manual              MEDIUMTEXT NOT NULL,
    tool_names          MEDIUMTEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE conversation (
    id                  VARCHAR(40) PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    title               VARCHAR(160) NOT NULL DEFAULT '新会话',
    summary             MEDIUMTEXT,
    summarized_until_id BIGINT,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conversation_user_id FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE chat_message (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id     VARCHAR(40) NOT NULL,
    role                VARCHAR(20) NOT NULL,
    content             MEDIUMTEXT NOT NULL,
    metadata            MEDIUMTEXT NOT NULL DEFAULT ('{}'),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_message_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE INDEX idx_chat_message_conversation ON chat_message(conversation_id, id);

CREATE TABLE conversation_skill (
    conversation_id     VARCHAR(40) NOT NULL,
    skill_code          VARCHAR(64) NOT NULL,
    loaded_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (conversation_id, skill_code),
    CONSTRAINT fk_conversation_skill_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_conversation_skill_skill_code FOREIGN KEY (skill_code) REFERENCES agent_skill(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE pending_action (
    id                  VARCHAR(40) PRIMARY KEY,
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    user_id             BIGINT NOT NULL,
    conversation_id     VARCHAR(40) NOT NULL,
    tool_name           VARCHAR(80) NOT NULL,
    arguments           MEDIUMTEXT NOT NULL,
    arguments_hash      VARCHAR(64) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    result              MEDIUMTEXT,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at          DATETIME(6),
    executed_at         DATETIME(6),
    CONSTRAINT fk_pending_action_user_id FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_pending_action_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE INDEX idx_pending_action_conversation ON pending_action(conversation_id, status, created_at);

CREATE TABLE user_memory (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    memory_key          VARCHAR(80) NOT NULL,
    memory_value        VARCHAR(500) NOT NULL,
    source_conversation_id VARCHAR(40),
    version             INTEGER NOT NULL DEFAULT 0,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (user_id, memory_key),
    CONSTRAINT fk_user_memory_user_id FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

