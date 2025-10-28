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

-- 商品向量表
CREATE TABLE IF NOT EXISTS item_vector (
    id BIGSERIAL PRIMARY KEY,
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
                                    id           BIGSERIAL PRIMARY KEY,
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
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
COMMENT ON COLUMN item.created_at     IS '创建时间';
COMMENT ON COLUMN item.updated_at     IS '更新时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_item_item_id    ON item(item_id);
CREATE INDEX IF NOT EXISTS idx_item_seller_id  ON item(seller_id);