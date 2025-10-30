CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64),
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS items (
    item_id VARCHAR(64) PRIMARY KEY,
    data JSONB,
    price VARCHAR(32),
    description TEXT,
    last_updated TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_bargain_counts (
    chat_id VARCHAR(64) PRIMARY KEY,
    count INTEGER DEFAULT 0,
    last_updated TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);
