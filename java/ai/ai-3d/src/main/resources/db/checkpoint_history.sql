-- checkpoint_history: Agent 检查点历史表，增量存储，不做删除
CREATE TABLE IF NOT EXISTS checkpoint_history (
    id            BIGINT PRIMARY KEY,
    thread_id     VARCHAR(64) NOT NULL,
    checkpoint_id VARCHAR(64) NOT NULL,
    node_id       VARCHAR(255),
    next_node_id  VARCHAR(255),
    state_data    JSONB NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_checkpoint_history_thread_id ON checkpoint_history(thread_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_history_checkpoint_id ON checkpoint_history(checkpoint_id);
