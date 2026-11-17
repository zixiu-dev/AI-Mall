-- Framework state includes assistant tool calls AND their results, isolated by user/session.
CREATE TABLE agent_scope_state (
    user_id VARCHAR(80) NOT NULL,
    session_id VARCHAR(80) NOT NULL,
    state_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key)
);
