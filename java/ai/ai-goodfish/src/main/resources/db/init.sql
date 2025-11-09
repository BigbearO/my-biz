-- 对话记忆表
CREATE TABLE IF NOT EXISTS chat_memory (
                                           id BIGINT PRIMARY KEY,
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

-- 商品向量表
CREATE TABLE IF NOT EXISTS item_vector (
                                           id BIGINT PRIMARY KEY,
                                           tenant_id VARCHAR(50) NOT NULL,
    item_id VARCHAR(50) NOT NULL,
    vector_data TEXT NOT NULL,
    item_content TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, item_id)
    );

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_item_vector_tenant_id ON item_vector(tenant_id);

-- 商品表
-- 商品表
CREATE TABLE IF NOT EXISTS item (
                                    id           BIGINT PRIMARY KEY,
                                    item_id      VARCHAR(100) NOT NULL UNIQUE,
    data         JSONB,
    price        DECIMAL(10,2),
    description  TEXT,
    title        VARCHAR(255),
    category     VARCHAR(100),
    location     VARCHAR(100),
    image_url    VARCHAR(500),
    seller_id    VARCHAR(100),
    condition    VARCHAR(50),
    published_date BIGINT,
    stock_status VARCHAR(50),
    created_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- 表注释
COMMENT ON TABLE item IS '商品表';

-- 列注释
COMMENT ON COLUMN item.item_id        IS '关联的闲鱼平台商品id';
COMMENT ON COLUMN item.data           IS '商品数据，JSON格式存储';
COMMENT ON COLUMN item.price          IS '商品价格';
COMMENT ON COLUMN item.description    IS '商品描述';
COMMENT ON COLUMN item.title          IS '商品标题';
COMMENT ON COLUMN item.category       IS '商品分类';
COMMENT ON COLUMN item.location       IS '商品所在地';
COMMENT ON COLUMN item.image_url      IS '商品图片URL';
COMMENT ON COLUMN item.seller_id      IS '卖家ID';
COMMENT ON COLUMN item.condition      IS '商品状态';
COMMENT ON COLUMN item.published_date IS '发布日期时间戳';
COMMENT ON COLUMN item.stock_status   IS '库存状态';
COMMENT ON COLUMN item.created_time     IS '创建时间';
COMMENT ON COLUMN item.updated_time     IS '更新时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_item_item_id    ON item(item_id);
CREATE INDEX IF NOT EXISTS idx_item_seller_id  ON item(seller_id);



CREATE TABLE IF NOT EXISTS account (
                                       id          BIGINT PRIMARY KEY,
                                       user_id     VARCHAR(64) NOT NULL,
    device_id   VARCHAR(64) NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP   NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_account_user_id    ON account(user_id);
-- 消息历史记录表
CREATE TABLE IF NOT EXISTS message_history (
                                               id BIGINT PRIMARY KEY,
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
COMMENT ON TABLE message_history IS '消息历史记录表';



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


ALTER TABLE item
    ADD COLUMN account_id BIGINT NOT NULL
    COMMENT '商品关联闲鱼账号';
