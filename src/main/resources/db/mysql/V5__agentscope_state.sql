-- MySQL 8.0.16+ / InnoDB
-- Framework state includes assistant tool calls AND their results, isolated by user/session.
CREATE TABLE agent_scope_state (
    user_id VARCHAR(80) NOT NULL,
    session_id VARCHAR(80) NOT NULL,
    state_key VARCHAR(120) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, session_id, state_key)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
