-- 初始化数据库表

-- 消息表
CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    item_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    chat_id VARCHAR(255) NOT NULL
);

-- 商品表
CREATE TABLE IF NOT EXISTS items (
    id SERIAL PRIMARY KEY,
    item_id VARCHAR(255) NOT NULL UNIQUE,
    data JSONB,
    price DECIMAL(10,2),
    description TEXT,
    title VARCHAR(255),
    category VARCHAR(100),
    location VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 议价次数表
CREATE TABLE IF NOT EXISTS chat_bargain_counts (
    chat_id VARCHAR(255) PRIMARY KEY,
    count INT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_user_item ON messages(user_id, item_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);