 -- 消息表
CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(50),
    item_id VARCHAR(50),
    role VARCHAR(20),
    content TEXT,
    timestamp TIMESTAMP,
    chat_id VARCHAR(100),
    CONSTRAINT idx_messages_chat_id FOREIGN KEY (chat_id) REFERENCES chat_bargain_counts(chat_id) ON DELETE CASCADE
);

-- 商品信息表
CREATE TABLE IF NOT EXISTS items (
    item_id VARCHAR(50) PRIMARY KEY,
    data TEXT,
    price DECIMAL(10, 2),
    description TEXT,
    last_updated TIMESTAMP
);

-- 议价次数表
CREATE TABLE IF NOT EXISTS chat_bargain_counts (
    chat_id VARCHAR(100) PRIMARY KEY,
    count INTEGER DEFAULT 0,
    last_updated TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);
CREATE INDEX IF NOT EXISTS idx_items_item_id ON items(item_id); 