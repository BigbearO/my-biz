-- 对话记忆表
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    memory_id VARCHAR(200) NOT NULL,
    item_id VARCHAR(50),
    conversation_id VARCHAR(100),
    messages TEXT NOT NULL,
    message_count INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, memory_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_chat_memory_tenant_id ON chat_memory(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_memory_item_id ON chat_memory(item_id);
CREATE INDEX IF NOT EXISTS idx_chat_memory_memory_id ON chat_memory(memory_id);

-- 消息历史记录表
CREATE TABLE IF NOT EXISTS message_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    memory_id VARCHAR(200) NOT NULL,
    chat_id VARCHAR(200) NOT NULL,
    item_id VARCHAR(50),
    role VARCHAR(20),
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(100),
    from_user_id VARCHAR(100)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_message_history_tenant_id ON message_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_message_history_memory_id ON message_history(memory_id);
CREATE INDEX IF NOT EXISTS idx_message_history_chat_id ON message_history(chat_id);
CREATE INDEX IF NOT EXISTS idx_message_history_item_id ON message_history(item_id);
CREATE INDEX IF NOT EXISTS idx_message_history_timestamp ON message_history(timestamp);

-- 表注释
COMMENT ON TABLE chat_memory IS '对话记忆表';
COMMENT ON TABLE message_history IS '消息历史记录表';

-- 列注释
COMMENT ON COLUMN chat_memory.id IS '主键ID';
COMMENT ON COLUMN chat_memory.tenant_id IS '租户ID';
COMMENT ON COLUMN chat_memory.memory_id IS '对话记忆ID';
COMMENT ON COLUMN chat_memory.item_id IS '商品ID';
COMMENT ON COLUMN chat_memory.conversation_id IS '会话ID';
COMMENT ON COLUMN chat_memory.messages IS '对话消息JSON';
COMMENT ON COLUMN chat_memory.message_count IS '消息数量';
COMMENT ON COLUMN chat_memory.create_time IS '创建时间';
COMMENT ON COLUMN chat_memory.update_time IS '更新时间';

COMMENT ON COLUMN message_history.id IS '主键ID';
COMMENT ON COLUMN message_history.tenant_id IS '租户ID';
COMMENT ON COLUMN message_history.memory_id IS '对话记忆ID';
COMMENT ON COLUMN message_history.chat_id IS '聊天ID';
COMMENT ON COLUMN message_history.item_id IS '商品ID';
COMMENT ON COLUMN message_history.role IS '角色';
COMMENT ON COLUMN message_history.content IS '消息内容';
COMMENT ON COLUMN message_history.type IS '消息类型(user/ai/system/tool)';
COMMENT ON COLUMN message_history.timestamp IS '消息时间戳';
COMMENT ON COLUMN message_history.update_time IS '更新时间';
COMMENT ON COLUMN message_history.user_id IS '用户ID';
COMMENT ON COLUMN message_history.from_user_id IS '来源用户ID';